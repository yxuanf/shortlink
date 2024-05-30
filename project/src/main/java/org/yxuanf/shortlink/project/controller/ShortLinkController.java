package org.yxuanf.shortlink.project.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.yxuanf.shortlink.project.common.convention.result.Result;
import org.yxuanf.shortlink.project.common.convention.result.Results;
import org.yxuanf.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import org.yxuanf.shortlink.project.service.ShortLinkService;

/**
 * 短链接管理控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/api/short-link/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkService.createShortLink(requestParam));
    }
}
