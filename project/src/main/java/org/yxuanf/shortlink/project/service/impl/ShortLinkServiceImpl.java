package org.yxuanf.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yxuanf.shortlink.project.common.convention.exception.ClientException;
import org.yxuanf.shortlink.project.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.project.common.enums.VailDateTypeEnum;
import org.yxuanf.shortlink.project.dao.entity.ShortLinkDO;
import org.yxuanf.shortlink.project.dao.entity.ShortLinkGotoDO;
import org.yxuanf.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import org.yxuanf.shortlink.project.dao.mapper.ShortLinkMapper;
import org.yxuanf.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkPageReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import org.yxuanf.shortlink.project.service.ShortLinkService;
import org.yxuanf.shortlink.project.toolkit.HashUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.yxuanf.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;
import static org.yxuanf.shortlink.project.common.constant.RedisKeyConstant.LOCK_GOTO_SHORT_LINK_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // 生成短链接后缀
        String shortLinkSuffix = generateSuffix(requestParam);
        // 生成完成短链接
        String fullShortUrl = requestParam.getDomain() + "/" + shortLinkSuffix;
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(requestParam.getDomain())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .fullShortUrl(fullShortUrl)
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
        LambdaQueryWrapper<ShortLinkDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        IPage<ShortLinkDO> result = baseMapper.selectPage(requestParam, lqw);
        return result.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
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
                .build();
        LambdaUpdateWrapper<ShortLinkDO> luw = new LambdaUpdateWrapper<>();
        // 短链接分组未被修改
        if (Objects.equals(hasShortLinkDO.getGid(), requestParam.getGid())) {
            luw.eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(Objects.equals(requestParam.getValidDateType(), VailDateTypeEnum.PERMANENT.getType()), ShortLinkDO::getValidDate, null);
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
    }

    // 短链接重定向
    @Override
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) throws IOException {
        // 返回主机名（nurl.ink）
        String serverName = request.getServerName();
        String fullShortUrl = serverName + "/" + shortUri;
        // 首先尝试从缓存中获取原始链接
        String originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(originalLink)) {
            ((HttpServletResponse) response).sendRedirect(originalLink);
            return;
        }
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
            // 双重判定锁，只有第一个拿到锁的请求进行缓存重构，之后拿到锁的请求直接查询缓存即可
            if (StrUtil.isNotBlank(originalLink)) {
                ((HttpServletResponse) response).sendRedirect(originalLink);
                return;
            }
            // 通过路由表获取短链接的gid
            LambdaQueryWrapper<ShortLinkGotoDO> link_Goto_lqw = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO linkGotoDO = shortLinkGotoMapper.selectOne(link_Goto_lqw);
            if (linkGotoDO == null) {
                //todo 需要风控
                return;
            }
            LambdaQueryWrapper<ShortLinkDO> lqw = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getGid, linkGotoDO.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(lqw);
            // 重定向原始链接（重定向URL）
            if (shortLinkDO != null) {
                // 第一个获取锁的进程才能添加
                stringRedisTemplate.opsForValue().set(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl), shortLinkDO.getOriginUrl());
                ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
            }
        } finally {
            lock.unlock();
        }
    }

    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        // 获取原始链接
        String originUrl = requestParam.getOriginUrl();
        String domain = requestParam.getDomain();
        int customGenerateCount = 0;
        String shortUri = null;
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
}
