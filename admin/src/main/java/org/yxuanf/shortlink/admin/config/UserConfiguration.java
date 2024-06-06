package org.yxuanf.shortlink.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yxuanf.shortlink.admin.common.biz.user.LoginInterceptor;
import org.yxuanf.shortlink.admin.common.biz.user.RefreshTokenInterceptor;

/**
 * 配置拦截器
 */
@Configuration(value = "userConfigurationByProject")
@RequiredArgsConstructor
public class UserConfiguration implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final StringRedisTemplate redisTemplate;

    /**
     * 用户信息传递拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor).excludePathPatterns(
                "/api/short-link/admin/v1/user/login",
                "/api/short-link/v1/user/has-username",
                "/api/short-link/admin/v1/title"
        ).order(10);

        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate))
                .addPathPatterns("/**").order(1);
    }
}