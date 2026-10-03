package com.shopsphere.product.config;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RedisConfig {
    // Basic EnableCaching setup. Spring Boot auto-configures RedisCacheManager based on application.yml properties.
}
