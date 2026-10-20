package com.yqz.openblog.sensitiveword.runtime;

import com.yqz.openblog.config.SensitiveWordProperties;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordVersionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 热切换只依赖当前快照词表，不触发 Redis/DB 重载。 */
class SensitiveWordManagerHotSwitchTest {

    private SensitiveWordManager newManager(SensitiveWordProperties properties) {
        return new SensitiveWordManager(
                mock(RedisOps.class),
                mock(StringRedisTemplate.class),
                properties,
                mock(SensitiveWordVersionMapper.class),
                mock(SensitiveWordMapper.class),
                new WordMatcherFactory(properties),
                "test",
                "0");
    }

    @Test
    void switchEngine_rebuildsFromSnapshotAndKeepsVersion() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEngine(SensitiveWordProperties.Engine.HOUBB);
        SensitiveWordManager manager = newManager(properties);
        ReflectionTestUtils.invokeMethod(manager, "applySnapshot", 7L, List.of("敏感词"), "checksum");

        assertEquals("houbb", manager.engine());
        assertTrue(manager.filter("这里有敏感词").hit());

        SensitiveWordManager.EngineSwitchOutcome outcome = manager.applyEngineOverride(SensitiveWordProperties.Engine.AC);

        assertEquals("ac", outcome.engine());
        assertEquals("houbb", outcome.previousEngine());
        assertTrue(outcome.applied());
        assertEquals(1, outcome.wordCount());
        assertEquals("ac", manager.engine());
        assertEquals(7L, manager.localVersion());
        assertEquals(1, manager.wordCount());
        assertTrue(manager.filter("这里有敏感词").hit());
        assertEquals("ac", manager.engineOverrideName());
    }

    @Test
    void clearOverride_fallsBackToConfiguredEngine() {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setEngine(SensitiveWordProperties.Engine.TRIE);
        SensitiveWordManager manager = newManager(properties);
        ReflectionTestUtils.invokeMethod(manager, "applySnapshot", 1L, List.of("甲乙"), "checksum");

        manager.applyEngineOverride(SensitiveWordProperties.Engine.HOUBB);
        assertEquals("houbb", manager.engine());

        manager.applyEngineOverride(null);
        assertEquals("trie", manager.engine());
        assertEquals(null, manager.engineOverrideName());
        assertTrue(manager.filter("甲乙").hit());
        assertFalse(manager.filter("普通文本").hit());
    }
}
