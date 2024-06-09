package org.yxuanf.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yxuanf.shortlink.project.common.convention.exception.ClientException;
import org.yxuanf.shortlink.project.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.project.common.enums.VailDateTypeEnum;
import org.yxuanf.shortlink.project.dao.entity.*;
import org.yxuanf.shortlink.project.dao.mapper.*;
import org.yxuanf.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkPageReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import org.yxuanf.shortlink.project.dto.resp.*;
import org.yxuanf.shortlink.project.service.ShortLinkService;
import org.yxuanf.shortlink.project.toolkit.HashUtil;
import org.yxuanf.shortlink.project.toolkit.LinkUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.yxuanf.shortlink.project.common.constant.RedisKeyConstant.*;
import static org.yxuanf.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;

    @Value("${shortLink.stats.locale.mapKey}")
    private String statsLocaleMapKey;
    @Value("${shortLink.domain.default}")
    private String createShortLinkDefaultDomain;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // 生成短链接后缀
        String shortLinkSuffix = generateSuffix(requestParam);
        // 设置指定域名
        String fullShortUrl = StrBuilder.create(createShortLinkDefaultDomain)
                .append("/")
                .append(shortLinkSuffix)
                .toString();
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(createShortLinkDefaultDomain)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .fullShortUrl(fullShortUrl)
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .build();
        // 创建短链接
        ShortLinkGotoDO linkGotoDO = ShortLinkGotoDO.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();
        try {
            // 作为兜底策略
            // 存在短链接入库成功，但Redis宕机的情况。此时通过唯一索引拦截
            baseMapper.insert(shortLinkDO);
            // 插入路由表，通过完整短链接找到gid从而获取完整链接
            shortLinkGotoMapper.insert(linkGotoDO);
            // 唯一索引冲突，查询数据库是否存在
        } catch (DuplicateKeyException ex) {
            // todo 误判短链接如何处理?
            // 1、短链接确实真实存在于缓存中
            // 2、短链接不一定存在于缓存中（redis宕机）
            LambdaQueryWrapper<ShortLinkDO> lqw = new LambdaQueryWrapper<>();
            lqw.eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO hasShortLink = baseMapper.selectOne(lqw);
            // 数据库中确实存在，抛异常
            if (hasShortLink != null) {
                log.warn("短链接{}重复入库", fullShortUrl);
                throw new ServiceException("短链接重复生成");
            }
        }
        // 进行缓存预热，将常见的数据提前放入缓存中
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()),
                TimeUnit.MILLISECONDS);
        // 将完整短链接加入到布隆过滤器
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .originUrl(shortLinkDO.getOriginUrl())
                .gid(shortLinkDO.getGid())
                .build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        IPage<ShortLinkDO> result = baseMapper.pageLink(requestParam);
        return result.convert(each -> {
            ShortLinkPageRespDTO bean = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            bean.setDomain("http://" + bean.getDomain());
            return bean;
        });
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        QueryWrapper<ShortLinkDO> qw = new QueryWrapper<>();
        // 需要保证gid唯一
        qw.select("gid as gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
                .groupBy("gid");
        List<Map<String, Object>> result = baseMapper.selectMaps(qw);
        return BeanUtil.copyToList(result, ShortLinkGroupCountRespDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // 判断短链接分组是否被修改
        LambdaQueryWrapper<ShortLinkDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        // 数据库之前的数据
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(lqw);
        if (hasShortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(hasShortLinkDO.getDomain())
                .shortUri(hasShortLinkDO.getShortUri())
                .favicon(hasShortLinkDO.getFavicon())
                .createdType(hasShortLinkDO.getCreatedType())
                .gid(requestParam.getGid())
                .originUrl(requestParam.getOriginUrl())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .build();
        LambdaUpdateWrapper<ShortLinkDO> luw = new LambdaUpdateWrapper<>();
        // 短链接分组未被修改
        if (Objects.equals(hasShortLinkDO.getGid(), requestParam.getGid())) {
            luw.eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(Objects.equals(requestParam.getValidDateType(), VailDateTypeEnum.PERMANENT.getType())
                            , ShortLinkDO::getValidDate, null);
            baseMapper.update(shortLinkDO, luw);
        }
        // 若分组被修改，则先删除原先的分组，在新添更新后的
        else {
            luw.eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, hasShortLinkDO.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            baseMapper.delete(luw);
            baseMapper.insert(shortLinkDO);
        }
        // 短链接保障缓存和数据库一致性(有效期)
        if (!Objects.equals(hasShortLinkDO.getValidDateType(), requestParam.getValidDateType())
                || !Objects.equals(hasShortLinkDO.getValidDate(), requestParam.getValidDate())
                || !Objects.equals(hasShortLinkDO.getOriginUrl(), requestParam.getOriginUrl())) {
            // 首先删除缓存
            stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
            Date currentDate = new Date();
            // 之前存在过期时间且已经过期
            if (hasShortLinkDO.getValidDate() != null && hasShortLinkDO.getValidDate().before(currentDate)) {
                // 设置为永久有效或有效
                if (Objects.equals(requestParam.getValidDateType(), VailDateTypeEnum.PERMANENT.getType())
                        || requestParam.getValidDate().after(currentDate)) {
                    // 删除缓存中数据库不存在标志
                    stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
                }
            }
        }
    }

    // 短链接重定向
    @Override
    @SneakyThrows
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) throws IOException {
        String serverName = request.getServerName();
        // 设置指定域名
        String serverPort = Optional.of(request.getServerPort())
                .filter(each -> !Objects.equals(each, 80))
                .map(String::valueOf)
                .map(each -> ":" + each)
                .orElse("");
        String fullShortUrl = serverName + serverPort + "/" + shortUri;
        // 首先尝试从缓存中获取原始链接
        String originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        // 不为空，直接返回
        if (StrUtil.isNotBlank(originalLink)) {
            //  统计方法要在重定向前调用，否则重定向后路径不对
            shortLinkStats(fullShortUrl, request, response);
            ((HttpServletResponse) response).sendRedirect(originalLink);
//            shortLinkStats(fullShortUrl, request, response);
            return;
        }
        // 若缓存中没有则判断布隆过滤器中是否存在完整短链接（避免恶意数据攻击）
        // 短链接创建过程时会将完整短链接加入布隆过滤器
        boolean contains = shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
        // 布隆过滤器中不存在就一定不存在，直接返回，说明没有创建该短链接（但可能将无判定为有）
        if (!contains) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        // 如果不为空值（"-"）,说明数据库不存在该信息，直接返回
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(gotoIsNullShortLink)) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
            // 双重判定锁，只有第一个拿到锁的请求进行缓存重构，之后拿到锁的请求直接查询缓存即可
            if (StrUtil.isNotBlank(originalLink)) {
                shortLinkStats(fullShortUrl, request, response);
                ((HttpServletResponse) response).sendRedirect(originalLink);
                return;
            }
            // 通过路由表获取短链接的gid
            LambdaQueryWrapper<ShortLinkGotoDO> link_Goto_lqw = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO linkGotoDO = shortLinkGotoMapper.selectOne(link_Goto_lqw);
            if (linkGotoDO == null) {
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            // 根据gid查询原始链接
            LambdaQueryWrapper<ShortLinkDO> lqw = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, linkGotoDO.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(lqw);
            // 重定向原始链接（重定向URL）
            if (shortLinkDO == null || (shortLinkDO.getValidDate() != null) && shortLinkDO.getValidDate().before(new Date())) {
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                    shortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()),
                    TimeUnit.MILLISECONDS);
            shortLinkStats(fullShortUrl, request, response);
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrls = requestParam.getOriginUrls();
        List<String> describes = requestParam.getDescribes();
        List<ShortLinkBaseInfoRespDTO> result = new ArrayList<>();
        for (int i = 0; i < originUrls.size(); i++) {
            ShortLinkCreateReqDTO shortLinkCreateReqDTO = BeanUtil.toBean(requestParam, ShortLinkCreateReqDTO.class);
            shortLinkCreateReqDTO.setOriginUrl(originUrls.get(i));
            shortLinkCreateReqDTO.setDescribe(describes.get(i));
            try {
                ShortLinkCreateRespDTO shortLink = createShortLink(shortLinkCreateReqDTO);
                ShortLinkBaseInfoRespDTO linkBaseInfoRespDTO = ShortLinkBaseInfoRespDTO.builder()
                        .fullShortUrl(shortLink.getFullShortUrl())
                        .originUrl(shortLink.getOriginUrl())
                        .describe(describes.get(i))
                        .build();
                result.add(linkBaseInfoRespDTO);
            } catch (Throwable ex) {
                log.error("批量创建短链接失败，原始参数：{}", originUrls.get(i));
            }
        }
        return ShortLinkBatchCreateRespDTO.builder()
                .total(result.size())
                .baseLinkInfos(result)
                .build();
    }

    /**
     * 短链接跳转监控
     */
    private void shortLinkStats(String fullShortUrl, ServletRequest request, ServletResponse response) {
        // 查看请求有没有cookies
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        // True 代表第一次访问
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        try {
            AtomicReference<String> uv = new AtomicReference<>();
            Runnable addResponseCookie = () -> {
                uv.set(UUID.fastUUID().toString());
                Cookie uvCookie = new Cookie("uv", uv.get());
                uvCookie.setMaxAge(60 * 60 * 24 * 30);
                // 设置cookie路径
                uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
                // 设置cookie
                ((HttpServletResponse) response).addCookie(uvCookie);
                uvFirstFlag.set(Boolean.TRUE);
                // 向redis中存放
                stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, uvCookie.getValue());
                // 设置cookies有效期为下一日零点到当前的时间差，保证确保用户的当日统计
                stringRedisTemplate.expire(SHORT_LINK_STATS_UV_KEY + fullShortUrl, secondsUntilNextHour(), TimeUnit.SECONDS);
            };
            if (ArrayUtil.isNotEmpty(cookies)) {
                Arrays.stream(cookies)
                        .filter(each -> Objects.equals(each.getName(), "uv"))
                        .findFirst()
                        .map(Cookie::getValue)
                        .ifPresentOrElse(each -> {
                            uv.set(each);
                            Long uvAdded = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UV_KEY + fullShortUrl, each);
                            stringRedisTemplate.expire(SHORT_LINK_STATS_UV_KEY + fullShortUrl, secondsUntilNextHour(), TimeUnit.SECONDS);
                            uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                        }, addResponseCookie);
            } else {
                addResponseCookie.run();
            }
            LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
            // 获取gid
            String gid = shortLinkGotoDO.getGid();
            // 根据cookies获取uv与pv
            String remoteAddr = LinkUtil.getActualIp((HttpServletRequest) request);
            Long uipAdded = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UIP_KEY + fullShortUrl, remoteAddr);
            stringRedisTemplate.expire(SHORT_LINK_STATS_UIP_KEY + fullShortUrl, secondsUntilNextHour(), TimeUnit.SECONDS);
            Date date = new Date();
            int hour = DateUtil.hour(date, true);
            int week = DateUtil.dayOfWeekEnum(date).getValue();
            int uip = (uipAdded != null && uipAdded > 0L) ? 1 : 0;
            int uvFirst = uvFirstFlag.get() ? 1 : 0;
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(uvFirst)
                    .uip(uip)
                    .hour(hour)
                    .weekday(week)
                    .fullShortUrl(fullShortUrl)
                    .date(date)
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
            // 获取地区（IP）信息
            HashMap<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleMapKey);
            localeParamMap.put("ip", remoteAddr);
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localJson = JSON.parseObject(localeResultStr);
            String infoCode = localJson.getString("infocode");
            if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                String province = StrUtil.equals(localJson.getString("province"), "[]") ? "Unknown" : localJson.getString("province");
                String city = StrUtil.equals(localJson.getString("city"), "[]") ? "Unknown" : localJson.getString("city");
                String adcode = StrUtil.equals(localJson.getString("adcode"), "[]") ? "Unknown" : localJson.getString("adcode");
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(province)
                        .city(city)
                        .adcode(adcode)
                        .country("China")
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .date(date)
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
                // 获取操作系统
                String os = LinkUtil.getOs((HttpServletRequest) request);
                LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .os(os)
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .date(date)
                        .build();
                linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
                // 获取浏览器
                String browser = LinkUtil.getBrowser((HttpServletRequest) request);
                LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .browser(browser)
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .date(date)
                        .build();
                linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

                // 访问设备统计
                String device = LinkUtil.getDevice((HttpServletRequest) request);
                LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                        .device(device)
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .date(date)
                        .build();
                linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
                // 访问网络统计
                String network = LinkUtil.getNetwork((HttpServletRequest) request);
                LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                        .network(network)
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .date(date)
                        .build();
                linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
                //  保存用户标识
                LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .user(uv.get())
                        .ip(remoteAddr)
                        .locale(StrUtil.join("-", "China", city, province))
                        .browser(browser)
                        .device(device)
                        .os(os)
                        .network(network)
                        .build();
                linkAccessLogsMapper.insert(linkAccessLogsDO);
                baseMapper.incrementStats(gid, fullShortUrl, 1, uvFirst, uip);
                LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                        .gid(gid)
                        .todayPv(1)
                        .todayUv(uvFirst)
                        .todayUip(uip)
                        .fullShortUrl(fullShortUrl)
                        .date(date)
                        .build();
                linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
            }
        } catch (Throwable ex) {
            log.error("短链接统计异常", ex);
        }
    }

    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        // 获取原始链接
        String originUrl = requestParam.getOriginUrl();
        String domain = requestParam.getDomain();
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后重试");
            }
            // 防止hash冲突
            originUrl += System.currentTimeMillis();
            // 创建短链接
            shortUri = HashUtil.hashToBase62(originUrl);
            // 若布隆过滤器容量接近饱和，误判概率显著加大（将不存在的数据判定为存在）
            if (!shortUriCreateCachePenetrationBloomFilter.contains(domain + "/" + shortUri)) {
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
    }

    @SneakyThrows
    private String getFavicon(String url) {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        int responseCode = connection.getResponseCode();
        // 如果是重定向响应码
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            // 获取重定向URL
            String redirectUrl = connection.getHeaderField("Location");
            if (redirectUrl != null) {
                URL newURL = new URL(redirectUrl);
                connection = (HttpURLConnection) newURL.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                responseCode = connection.getResponseCode();
            }
        }
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Document document = Jsoup.connect(url).get();
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            if (faviconLink != null) {
                return faviconLink.attr("abs:href");
            }
        }
        return null;
    }

    private long secondsUntilNextHour() {
        LocalDateTime now = LocalDateTime.now();
        // 计算下一个整点时间
        LocalDateTime nextHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
        // 计算当前时间到下一个整点时间的秒数
        Duration duration = Duration.between(now, nextHour);
        return duration.getSeconds();
    }
}
