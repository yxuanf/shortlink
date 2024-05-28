package org.yxuanf.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yxuanf.shortlink.admin.common.convention.result.Result;
import org.yxuanf.shortlink.admin.common.convention.result.Results;
import org.yxuanf.shortlink.admin.dto.req.UserRegisterReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserActualRespDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;
import org.yxuanf.shortlink.admin.service.UserService;

/**
 * 用户管理数据层
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {
    /**
     * 根据用户名查询用户信息
     */
    private final UserService userService;

    @GetMapping("/api/short-link/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
        return Results.success(userService.getUserByUsername(username));
    }

    @GetMapping("/api/short-link/v1/actual/user/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username) {
        return Results.success(BeanUtil.toBean(userService.getUserByUsername(username), UserActualRespDTO.class));
    }

    /**
     * 查询用户名是否存在
     */
    @GetMapping("/api/short-link/v1/user/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userService.hasUsername(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/api/short-link/admin/v1/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }
}
