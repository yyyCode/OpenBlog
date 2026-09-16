package com.yqz.openblog.feedback.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.feedback.dto.FeedbackCreateRequest;
import com.yqz.openblog.feedback.entity.FeedbackEntry;
import com.yqz.openblog.feedback.repo.FeedbackRepository;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 反馈限额维度的单元测试：从「按 IP」改为「按登录用户」后的四条关键路径。
 * 注意最后一个用例：Redis 故障时 hasKey 返回 false（framework-redis 的 fail-safe 契约），
 * 必须继续走 DB 判定而不是误拒——这正是改造前 setIfAbsent 返回 false 被当成"已提交"的老 bug。
 */
class FeedbackServiceTest {

    private static final long UID = 42L;

    private FeedbackRepository feedbackRepository;
    private UserRepository userRepository;
    private RedisOps redisOps;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(FeedbackRepository.class);
        userRepository = mock(UserRepository.class);
        redisOps = mock(RedisOps.class);
        service = new FeedbackService(feedbackRepository, userRepository, redisOps);
    }

    @Test
    void create_rejectsWhenUserMissing() {
        when(userRepository.findById(UID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.create(req(), UID, request()));

        assertEquals(4030, ex.getCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenUserNotActive() {
        when(userRepository.findById(UID)).thenReturn(Optional.of(user("PENDING")));

        BizException ex = assertThrows(BizException.class, () -> service.create(req(), UID, request()));

        assertEquals(4030, ex.getCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenRedisFastPathHits() {
        when(userRepository.findById(UID)).thenReturn(Optional.of(user("ACTIVE")));
        when(redisOps.hasKey(anyString())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.create(req(), UID, request()));

        assertEquals(4290, ex.getCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenDbAlreadyHasTodayRow() {
        when(userRepository.findById(UID)).thenReturn(Optional.of(user("ACTIVE")));
        when(redisOps.hasKey(anyString())).thenReturn(false);
        when(feedbackRepository.existsByUserIdAndSubmitDay(any(), any())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.create(req(), UID, request()));

        assertEquals(4290, ex.getCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void create_rejectsOnConcurrentUniqueKeyConflict() {
        when(userRepository.findById(UID)).thenReturn(Optional.of(user("ACTIVE")));
        when(redisOps.hasKey(anyString())).thenReturn(false);
        when(feedbackRepository.existsByUserIdAndSubmitDay(any(), any())).thenReturn(false);
        when(feedbackRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        BizException ex = assertThrows(BizException.class, () -> service.create(req(), UID, request()));

        assertEquals(4290, ex.getCode());
    }

    @Test
    void create_savesWithJwtIdentityAndWarmsCache() {
        when(userRepository.findById(UID)).thenReturn(Optional.of(user("ACTIVE")));
        when(redisOps.hasKey(anyString())).thenReturn(false);
        when(feedbackRepository.existsByUserIdAndSubmitDay(any(), any())).thenReturn(false);

        service.create(req(), UID, request());

        ArgumentCaptor<FeedbackEntry> saved = ArgumentCaptor.forClass(FeedbackEntry.class);
        verify(feedbackRepository).save(saved.capture());
        FeedbackEntry e = saved.getValue();
        assertEquals(UID, e.getUserId());
        // 提交人姓名取自账号，而非请求体（请求体现在只有 content）
        assertEquals("alice", e.getSubmitterName());
        assertEquals(LocalDate.now(), e.getSubmitDay());
        assertEquals("反馈内容", e.getContent());

        verify(feedbackRepository).existsByUserIdAndSubmitDay(UID, LocalDate.now());
        verify(redisOps).set(anyString(), anyString(), any());
    }

    @Test
    void create_redisDown_dbGuardStillBlocksDuplicate() {
        when(userRepository.findById(UID)).thenReturn(Optional.of(user("ACTIVE")));
        // Redis 故障或缓存被清空：hasKey 走 fail-safe 返回 false，
        // 此时不能误放行，必须由 DB 的唯一键判据兜住"每天一次"
        when(redisOps.hasKey(anyString())).thenReturn(false);
        when(feedbackRepository.existsByUserIdAndSubmitDay(any(), any())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.create(req(), UID, request()));

        assertEquals(4290, ex.getCode());
        verify(feedbackRepository, never()).save(any());
    }

    private static FeedbackCreateRequest req() {
        FeedbackCreateRequest r = new FeedbackCreateRequest();
        r.setContent("反馈内容");
        return r;
    }

    private static HttpServletRequest request() {
        return mock(HttpServletRequest.class);
    }

    private static User user(String status) {
        User u = new User();
        u.setUsername("alice");
        u.setStatus(status);
        return u;
    }
}
