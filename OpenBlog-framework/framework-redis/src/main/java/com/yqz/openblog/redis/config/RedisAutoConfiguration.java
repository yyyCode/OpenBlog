package com.yqz.openblog.redis.config;

import com.yqz.openblog.redis.core.DefaultRedisOps;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.redis.limiter.SlidingWindowLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisOps redisOps(StringRedisTemplate redisTemplate) {
        return new DefaultRedisOps(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowLimiter slidingWindowLimiter(StringRedisTemplate redisTemplate) {
        return new SlidingWindowLimiter(redisTemplate);
    }
}
