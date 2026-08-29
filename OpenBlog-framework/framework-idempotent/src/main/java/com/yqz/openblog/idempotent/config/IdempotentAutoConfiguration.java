package com.yqz.openblog.idempotent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.idempotent.aspect.RepeatExecuteLimitAspect;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.handle.RedisRepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.redis.core.RedisOps;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(IdempotentProperties.class)
@ConditionalOnProperty(prefix = "openblog.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteKeyBuilder repeatExecuteKeyBuilder(IdempotentProperties props, Environment env) {
        String envPrefix = props.getEnvPrefix();
        if (envPrefix == null || envPrefix.isBlank()) {
            String[] profiles = env.getActiveProfiles();
            envPrefix = profiles.length > 0 ? profiles[0] : "dev";
        }
        return new RepeatExecuteKeyBuilder(envPrefix);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteKeyResolver repeatExecuteKeyResolver() {
        return new RepeatExecuteKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalLockCache localLockCache(IdempotentProperties props) {
        return new LocalLockCache(props.getLocalLockCacheHours());
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteLockSupport repeatExecuteLockSupport(RedissonClient redissonClient) {
        return new RepeatExecuteLockSupport(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteFlagStore repeatExecuteFlagStore(RedisOps redisOps) {
        return new RedisRepeatExecuteFlagStore(redisOps);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(
            RepeatExecuteKeyResolver keyResolver,
            RepeatExecuteKeyBuilder keyBuilder,
            RepeatExecuteLockSupport lockSupport,
            LocalLockCache localLockCache,
            RepeatExecuteFlagStore flagStore,
            ObjectMapper objectMapper) {
        return new RepeatExecuteLimitAspect(
                keyResolver, keyBuilder, lockSupport, localLockCache, flagStore, objectMapper);
    }
}
