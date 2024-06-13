package org.yxuanf.shortlink.project.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.yxuanf.shortlink.project.common.convention.result.Result;
import org.yxuanf.shortlink.project.common.convention.result.Results;
import org.yxuanf.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkPageReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkBatchCreateRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import org.yxuanf.shortlink.project.handler.CustomBlockHandler;
import org.yxuanf.shortlink.project.service.ShortLinkService;

import java.io.IOException;
import java.util.List;

/**
 * 短链接管理控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/v1/create")
    @SentinelResource(value = "create_short-link",
            blockHandler = "createShortLinkBlockHandlerMethod",
            blockHandlerClass = CustomBlockHandler.class)
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkService.createShortLink(requestParam));
    }

    /**
     * 根据gid以及排序方式分页查询短链接
     */
    @GetMapping("/api/short-link/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return Results.success(shortLinkService.pageShortLink(requestParam));
    }

    /**
     * 查询短链接分组内数量
     */
    @GetMapping("/api/short-link/v1/count")
    public Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(@RequestParam("requestParam") List<String> requestParam) {
        return Results.success(shortLinkService.listGroupShortLinkCount(requestParam));
    }

    /**
     * 修改短链接
     */
    @PostMapping("/api/short-link/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        shortLinkService.updateShortLink(requestParam);
        return Results.success();
    }

    /**
     * 短链接跳转原始链接
     */
    @GetMapping("/{short-uri}")
    public void restoreUrl(@PathVariable("short-uri") String shortUri, ServletRequest request, ServletResponse response) throws IOException {
        shortLinkService.restoreUrl(shortUri, request, response);
    }

    /**
     * 批量创建短链接
     */
    @PostMapping("/api/short-link/v1/create/batch")
    public Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam) {
        return Results.success(shortLinkService.batchCreateShortLink(requestParam));
    }

    /**
     * 忽略favicon.ico
     */
    @GetMapping("/favicon.ico")
    public void returnNoFavicon() {
    }
}

