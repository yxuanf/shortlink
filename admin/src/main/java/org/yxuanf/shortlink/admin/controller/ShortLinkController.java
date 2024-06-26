package org.yxuanf.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.yxuanf.shortlink.admin.common.convention.result.Result;
import org.yxuanf.shortlink.admin.dto.req.ShortLinkBatchCreateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.ShortLinkBaseInfoRespDTO;
import org.yxuanf.shortlink.admin.dto.resp.ShortLinkBatchCreateRespDTO;
import org.yxuanf.shortlink.admin.remote.ShortLinkRemoteService;
import org.yxuanf.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import org.yxuanf.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import org.yxuanf.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import org.yxuanf.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import org.yxuanf.shortlink.admin.toolkit.EasyExcelWebUtil;

import java.util.List;

@RestController

public class ShortLinkController {
    @Resource
    private ShortLinkRemoteService shortLinkRemoteService;

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkRemoteService.createShortLink(requestParam);
    }

    /**
     * 修改短链接
     */
    @PostMapping("/api/short-link/admin/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        return shortLinkRemoteService.updateShortLink(requestParam);
    }

    /**
     * 分页查询短链接
     */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkRemoteService.pageShortLink(requestParam);
    }

    /**
     * 批量创建短链接
     */
    @SneakyThrows
    @PostMapping("/api/short-link/admin/v1/create/batch")
    public void batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam, HttpServletResponse response) {
        Result<ShortLinkBatchCreateRespDTO> shortLinkBatchCreateRespDTOResult =shortLinkRemoteService.batchCreateShortLink(requestParam);
        if (shortLinkBatchCreateRespDTOResult.isSuccess()) {
            List<ShortLinkBaseInfoRespDTO> baseLinkInfos = shortLinkBatchCreateRespDTOResult.getData().getBaseLinkInfos();
            EasyExcelWebUtil.write(response, "批量创建短链接-SaaS短链接系统", ShortLinkBaseInfoRespDTO.class, baseLinkInfos);
        }
    }
}
