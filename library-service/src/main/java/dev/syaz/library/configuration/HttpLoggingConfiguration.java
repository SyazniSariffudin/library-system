package dev.syaz.library.configuration;

import dev.syaz.library.filter.HttpLoggingFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpLoggingProperties.class)
class HttpLoggingConfiguration {
    @Bean
    FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter(HttpLoggingProperties properties) {
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>(new HttpLoggingFilter(properties));
        registration.setName("httpLoggingFilter");
        registration.addUrlPatterns("/api/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        // Run after Spring Boot's character encoding filter, before the controllers.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
