package org.yxuanf.shortlink.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.yxuanf.shortlink.admin.common.convention.result.Result;
import org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum;
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
       UserRespDTO result = userService.getUserByUsername(username);
        if (result == null) {
            return new Result<UserRespDTO>().setCode(UserErrorCodeEnum.USER_NULL.code()).setMessage(UserErrorCodeEnum.USER_NULL.message());
        } else
            return new Result<UserRespDTO>().setCode("0").setData(userService.getUserByUsername(username));
    }

}
