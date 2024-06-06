package org.yxuanf.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.yxuanf.shortlink.project.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkStatsRespDTO;

/**
 * 短链接监控接口层
 */
public interface ShortLinkStatsService {

    /**
     * 单个短链接在指定时间内的监控数据（按日划分）
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);

    /**
     * 访问某个分组内所有短链接指定时间内监控数据（按日划分）
     *
     * @param requestParam 获取分组短链接监控数据入参
     * @return 分组短链接监控数据
     */
    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam);

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     *
     * @param requestParam 获取短链接监控访问记录数据入参
     * @return 访问记录监控数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam);

    /**
     * 分组短链接指定时间内的访问日志
     * * @param requestParam 获取短链接监控访问记录数据入参
     * * @return 访问记录监控数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO requestParam);
}