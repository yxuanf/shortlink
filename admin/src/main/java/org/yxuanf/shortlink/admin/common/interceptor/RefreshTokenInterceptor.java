package org.yxuanf.shortlink.admin.common.interceptor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.yxuanf.shortlink.admin.common.biz.user.UserContext;
import org.yxuanf.shortlink.admin.common.biz.user.UserInfoDTO;

import java.util.concurrent.TimeUnit;

import static org.yxuanf.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;

/**
 * 登录拦截器第一级（刷新token）
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) throws Exception {
        // 获取username 以及 token
        String username = httpServletRequest.getHeader("username");
        String token = httpServletRequest.getHeader("token");
        // 如果没有收到请求头
        if (!StrUtil.isAllNotBlank(username, token)) {
            return true;
        }
        // 获取用户信息
        Object userInfoJsonStr = stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token);
        if (userInfoJsonStr != null) {
            // 将用户信息放在用户上下文中
            UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
            UserContext.setUser(userInfoDTO);
            // 刷新token有效期
            stringRedisTemplate.expire(USER_LOGIN_KEY + username, 24 * 10, TimeUnit.HOURS);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
