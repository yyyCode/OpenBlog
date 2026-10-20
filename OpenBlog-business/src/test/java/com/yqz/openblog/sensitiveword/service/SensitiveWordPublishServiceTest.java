package com.yqz.openblog.sensitiveword.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.SensitiveWordProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.sensitiveword.dto.SensitiveWordDtos;
import com.yqz.openblog.sensitiveword.entity.SensitiveWordVersion;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordChangeLogMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordVersionMapper;
import com.yqz.openblog.sensitiveword.runtime.SnapshotCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发布/同步的关键失败路径；不依赖真实 Redis / MySQL。 */
class SensitiveWordPublishServiceTest {

    private SensitiveWordMapper wordMapper;
    private SensitiveWordVersionMapper versionMapper;
    private SensitiveWordChangeLogMapper changeLogMapper;
    private SensitiveWordStore store;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CurrentUser currentUser;
    private SensitiveWordPublishService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 纯单测没有 MyBatis 启动过程，先初始化实体元数据，lambdaUpdate() 才能解析列名
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SensitiveWordVersion.class);

        wordMapper = mock(SensitiveWordMapper.class);
        versionMapper = mock(SensitiveWordVersionMapper.class);
        changeLogMapper = mock(SensitiveWordChangeLogMapper.class);
        store = mock(SensitiveWordStore.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        currentUser = mock(CurrentUser.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(currentUser.userId()).thenReturn(1L);

        SensitiveWordProperties properties = new SensitiveWordProperties();
        service = new SensitiveWordPublishService(wordMapper, versionMapper, changeLogMapper, store,
                properties, redisTemplate, mock(PlatformTransactionManager.class), currentUser);
    }

    @Test
    void publish_emptyWordSet_rejectedUnlessAllowed() {
        when(store.enabledWords()).thenReturn(List.of());

        BizException e = assertThrows(BizException.class, () -> service.publish(null, false));
        assertEquals(4000, e.getCode());
        verify(versionMapper, never()).insert(any(SensitiveWordVersion.class));
    }

    @Test
    void publish_emptyWordSet_allowedWhenExplicit() {
        when(store.enabledWords()).thenReturn(List.of());
        assignIds(3L, null);

        SensitiveWordDtos.PublishResponse response = service.publish("清空词库", true);

        assertEquals(3L, response.version());
        assertEquals(0, response.wordCount());
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
        assertEquals("PUBLISHED", lastStatus());
    }

    @Test
    void publish_lockConflict_returns4092() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.publish(null, false));
        assertEquals(4092, e.getCode());
    }

    @Test
    void publish_redisFailure_marksVersionFailed() {
        when(store.enabledWords()).thenReturn(List.of("敏感词"));
        assignIds(5L, null);
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        BizException e = assertThrows(BizException.class, () -> service.publish(null, false));

        assertEquals(5001, e.getCode());
        assertEquals("FAILED", lastStatus());
    }

    @Test
    void publish_checksumMismatch_refusesToDistribute() {
        when(store.enabledWords()).thenReturn(List.of("敏感词"));
        assignIds(6L, "0000000000000000000000000000000000000000000000000000000000000000");

        BizException e = assertThrows(BizException.class, () -> service.publish(null, false));

        assertEquals(5001, e.getCode());
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        assertEquals("FAILED", lastStatus());
    }

    @Test
    void sync_olderThanCurrent_rejected() {
        when(versionMapper.selectById(4L)).thenReturn(version(4L, List.of("旧词"), "PUBLISHED"));
        when(valueOps.get(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT)).thenReturn("9");

        BizException e = assertThrows(BizException.class, () -> service.sync(4L));

        assertEquals(4000, e.getCode());
        verify(versionMapper, never()).update(any(), any());
    }

    @Test
    void sync_redisFailure_doesNotChangeVersionStatus() {
        when(versionMapper.selectById(9L)).thenReturn(version(9L, List.of("词"), "PUBLISHED"));
        when(valueOps.get(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT)).thenReturn(null);
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        BizException e = assertThrows(BizException.class, () -> service.sync(9L));

        assertEquals(5001, e.getCode());
        verify(versionMapper, never()).update(any(), any());
    }

    private void assignIds(Long id, String checksumOverride) {
        doAnswer(invocation -> {
            SensitiveWordVersion v = invocation.getArgument(0);
            v.setId(id);
            if (checksumOverride != null) {
                v.setChecksum(checksumOverride);
            }
            return 1;
        }).when(versionMapper).insert(any(SensitiveWordVersion.class));
    }

    private SensitiveWordVersion version(long id, List<String> words, String status) {
        byte[] plain = SnapshotCodec.encode(words);
        SensitiveWordVersion v = new SensitiveWordVersion();
        v.setId(id);
        v.setStatus(status);
        v.setChecksum(SnapshotCodec.sha256Hex(plain));
        v.setSnapshotBlob(SnapshotCodec.gzip(plain));
        v.setWordCount(words.size());
        return v;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String lastStatus() {
        ArgumentCaptor<Wrapper<SensitiveWordVersion>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(versionMapper, org.mockito.Mockito.atLeastOnce()).update(isNull(), captor.capture());
        AbstractWrapper wrapper = (AbstractWrapper) captor.getValue();
        return String.valueOf(wrapper.getParamNameValuePairs().values().stream()
                .filter(v -> "PUBLISHED".equals(v) || "FAILED".equals(v))
                .findFirst().orElse(null));
    }
}
