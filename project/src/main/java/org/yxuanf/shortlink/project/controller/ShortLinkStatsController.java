package org.yxuanf.shortlink.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.yxuanf.shortlink.project.common.convention.result.Result;
import org.yxuanf.shortlink.project.common.convention.result.Results;
import org.yxuanf.shortlink.project.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkStatsRespDTO;
import org.yxuanf.shortlink.project.service.ShortLinkStatsService;

/**
 * 短链接监控控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkStatsService shortLinkStatsService;

    /**
     * 单个短链接在指定时间内的监控数据（按日划分）
     */
    @GetMapping("/api/short-link/v1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(@RequestBody ShortLinkStatsReqDTO requestParam) {
        return Results.success(shortLinkStatsService.oneShortLinkStats(requestParam));
    }

    /**
     * 访问某个分组内所有短链接指定时间内监控数据（按日划分）
     */
    @GetMapping("/api/short-link/v1/stats/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(@RequestBody ShortLinkGroupStatsReqDTO requestParam) {
        return Results.success(shortLinkStatsService.groupShortLinkStats(requestParam));
    }

    /**
     * 单个短链接指定时间内的访问日志
     */
    @GetMapping("/api/short-link/v1/stats/access-record")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(@RequestBody ShortLinkStatsAccessRecordReqDTO requestParam) {
        return Results.success(shortLinkStatsService.shortLinkStatsAccessRecord(requestParam));
    }

    /**
     * 分组短链接指定时间内的访问日志
     */
    @GetMapping("/api/short-link/v1/stats/access-record/group")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(@RequestBody ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        return Results.success(shortLinkStatsService.groupShortLinkStatsAccessRecord(requestParam));
    }
}
