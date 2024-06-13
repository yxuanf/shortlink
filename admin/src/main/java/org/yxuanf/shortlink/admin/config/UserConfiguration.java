package org.yxuanf.shortlink.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.yxuanf.shortlink.admin.common.interceptor.LoginInterceptor;
import org.yxuanf.shortlink.admin.common.interceptor.RefreshTokenInterceptor;
import org.yxuanf.shortlink.admin.common.interceptor.UserFlowRiskInterceptor;

/**
 * 配置拦截器
 */
@Configuration(value = "userConfigurationByProject")
@RequiredArgsConstructor
public class UserConfiguration implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final StringRedisTemplate redisTemplate;
    private final UserFlowRiskInterceptor userFlowRiskInterceptor;

    @Value("${short-link.flow-limit.enable}")
    private boolean flowRiskFlag;

    /**
     * 用户信息传递拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate))
                .addPathPatterns("/**").order(1);

        // 是否启动风控
        if (flowRiskFlag) {
            registry.addInterceptor(userFlowRiskInterceptor)
                    .addPathPatterns("/**").order(5);
        }

        registry.addInterceptor(loginInterceptor).excludePathPatterns(
                "/api/short-link/admin/v1/user/login",
                "/api/short-link/v1/user/has-username",
                "/api/short-link/admin/v1/title"
        ).order(10);
    }
}