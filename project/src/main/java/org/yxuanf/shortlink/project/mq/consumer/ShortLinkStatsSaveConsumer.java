package org.yxuanf.shortlink.project.mq.consumer;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.yxuanf.shortlink.project.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.project.dao.entity.*;
import org.yxuanf.shortlink.project.dao.mapper.*;
import org.yxuanf.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import org.yxuanf.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.yxuanf.shortlink.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static org.yxuanf.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveConsumer implements StreamListener<String, MapRecord<String, String, String>> {
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
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;

    @Value("${shortLink.stats.locale.mapKey}")
    private String statsLocaleMapKey;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String stream = message.getStream();
        RecordId id = message.getId();

        // 判断当前消息是否消费过
        if (!messageQueueIdempotentHandler.isMessageBeingConsumed(id.toString())) {
            // 判断当前的这个消息流程是否执行完成(是否消费成功)
            if (messageQueueIdempotentHandler.isAccomplish(id.toString())) {
                return;
            }
            throw new ServiceException("消息未完成流程，需要消息队列重试");
        }
        try {
            Map<String, String> producerMap = message.getValue();
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
            actualSaveShortLinkStats(statsRecord);
            stringRedisTemplate.opsForStream().delete(Objects.requireNonNull(stream), id.getValue());
        } catch (Throwable ex) {
            // 某某某情况宕机了,如果消息处理遇到异常情况，删除幂等标识
            messageQueueIdempotentHandler.delMessageProcessed(id.toString());
            log.error("记录短链接监控消费异常", ex);
            throw ex;
        }
        // 设置消息流程执行完成
        messageQueueIdempotentHandler.setAccomplish(id.toString());
    }

    public void actualSaveShortLinkStats(ShortLinkStatsRecordDTO statsRecord) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        // 获取读锁
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        rLock.lock();
        try {
            LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
            String gid = shortLinkGotoDO.getGid();
            Date currentDate = statsRecord.getCurrentDate();
            int hour = DateUtil.hour(currentDate, true);
            Week week = DateUtil.dayOfWeekEnum(currentDate);
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .uip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
            Map<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleMapKey);
            localeParamMap.put("ip", statsRecord.getRemoteAddr());
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localJson = JSON.parseObject(localeResultStr);
            String infoCode = localJson.getString("infocode");
            String province = "Unknown";
            String city = "Unknown";
            String adcode = "Unknown";
            if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                province = StrUtil.equals(localJson.getString("province"), "[]") ? "Unknown" : localJson.getString("province");
                city = StrUtil.equals(localJson.getString("city"), "[]") ? "Unknown" : localJson.getString("city");
                adcode = StrUtil.equals(localJson.getString("adcode"), "[]") ? "Unknown" : localJson.getString("adcode");
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(province)
                        .city(city)
                        .adcode(adcode)
                        .country("China")
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .date(currentDate)
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
            }
            // 操作系统
            LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .os(statsRecord.getOs())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
            // 浏览器
            LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);
            // 访问设备
            LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .device(statsRecord.getDevice())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
            // 访问网络
            LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
            // 监控日志
            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .user(statsRecord.getUv())
                    .ip(statsRecord.getRemoteAddr())
                    .browser(statsRecord.getBrowser())
                    .os(statsRecord.getOs())
                    .network(statsRecord.getNetwork())
                    .device(statsRecord.getDevice())
                    .locale(StrUtil.join("-", "中国", province, city))
                    .fullShortUrl(fullShortUrl)
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);
            // 短链接统计
            shortLinkMapper.incrementStats(gid, fullShortUrl, 1, statsRecord.getUvFirstFlag() ? 1 : 0, statsRecord.getUipFirstFlag() ? 1 : 0);
            LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                    .gid(statsRecord.getGid())
                    .todayPv(1)
                    .todayUv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .todayUip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
        } catch (Throwable ex) {
            log.error("短链接访问量统计异常", ex);
        } finally {
            rLock.unlock();
        }
    }


}
