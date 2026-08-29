package com.yqz.openblog.idempotent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.idempotent.aspect.RepeatExecuteLimitAspect;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.redis.core.RedisOps;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotentAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotentAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
            .withBean(RedisOps.class, () -> mock(RedisOps.class));

    @Test
    void assemblesIdempotencyBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RepeatExecuteLimitAspect.class);
            assertThat(context).hasSingleBean(RepeatExecuteKeyBuilder.class);
            assertThat(context).hasSingleBean(RepeatExecuteKeyResolver.class);
            assertThat(context).hasSingleBean(LocalLockCache.class);
            assertThat(context).hasSingleBean(RepeatExecuteLockSupport.class);
            assertThat(context).hasSingleBean(RepeatExecuteFlagStore.class);
        });
    }
}
