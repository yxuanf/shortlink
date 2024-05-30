package org.yxuanf.shortlink.admin.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.yxuanf.shortlink.admin.common.biz.user.UserTransmitFilter;

/**
 * 用户配置自动装配
 */
@Configuration
public class UserConfiguration {
    /**
     * 用户信息传递过滤器
     */
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> globalUserTransmitFilter(StringRedisTemplate stringRedisTemplate) {
        FilterRegistrationBean<UserTransmitFilter> registration = new FilterRegistrationBean<>();
        // 注册过滤器
        registration.setFilter(new UserTransmitFilter(stringRedisTemplate));
        registration.addUrlPatterns("/*");
        // 放行登录功能的URL
        registration.addInitParameter("excludedUris","/api/short-link/admin/v1/user/login");
        registration.setOrder(0);

        return registration;
    }
}