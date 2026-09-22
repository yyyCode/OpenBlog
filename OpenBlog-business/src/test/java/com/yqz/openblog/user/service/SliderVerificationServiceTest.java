package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.dto.SliderChallengeResponse;
import com.yqz.openblog.user.dto.SliderCompleteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 滑块三件套（坐标容差 / 一次性消费 / 轨迹）的拒绝路径逐一覆盖。
 * 这些用例是安全基线：任何一条放开都意味着某一类绕过重新可用。
 */
@ExtendWith(MockitoExtension.class)
class SliderVerificationServiceTest {

    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";
    private static final int TARGET_X = 180;

    @Mock private RedisOps redisOps;

    private AuthSecurityProperties props;
    private SliderVerificationService service;

    @BeforeEach
    void setUp() {
        props = new AuthSecurityProperties();
        props.getSlider().setEnabled(true);
        service = new SliderVerificationService(redisOps, props);
    }

    // ==================== 签发 ====================

    @Test
    void issue_storesTargetXAndIpButNeverReturnsTargetX() {
        SliderChallengeResponse r = service.issue(request(IP));

        assertTrue(r.isEnabled());
        assertNotNull(r.getChallengeId());
        assertNotNull(r.getBackground());
        assertNotNull(r.getSlider());
        assertNotNull(r.getSliderY());

        // 缺口真值只能落 Redis；响应里只有图，客户端得自己做像素识别
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(redisOps).set(eq(RedisKeys.sliderPending(r.getChallengeId())), value.capture(), any());
        String[] parts = value.getValue().split(":");
        assertEquals(2, parts.length);
        assertEquals(IP, parts[1]);
        int storedX = Integer.parseInt(parts[0]);
        assertTrue(storedX >= SliderImageGenerator.MIN_X && storedX <= SliderImageGenerator.MAX_X,
                "缺口 x 越界: " + storedX);
    }

    @Test
    void issue_disabled_returnsDisabledFlagWithoutTouchingRedis() {
        props.getSlider().setEnabled(false);

        SliderChallengeResponse r = service.issue(request(IP));

        assertEquals(false, r.isEnabled());
        verifyNoInteractions(redisOps);
    }

    // ==================== complete 通过路径 ====================

    @Test
    void complete_correctXWithHumanTrail_writesOneTimeOkMarker() {
        String id = "c-ok";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        service.complete(request(IP), withTrail(id, TARGET_X, HUMAN_FRACTIONS, HUMAN_TIMES));

        verify(redisOps).set(RedisKeys.sliderOk(id), "1",
                Duration.ofSeconds(props.getSlider().getTtlSeconds()));
    }

    @Test
    void complete_disabled_passesThroughWithoutTouchingRedis() {
        props.getSlider().setEnabled(false);

        service.complete(request(IP), withTrail("c1", TARGET_X, HUMAN_FRACTIONS, HUMAN_TIMES));

        verifyNoInteractions(redisOps);
    }

    // ==================== complete 拒绝路径 ====================

    @Test
    void complete_unknownOrAlreadyUsedChallenge_rejects() {
        // getAndDelete 一次性：重放同一 challengeId 时这里已取不到值
        when(redisOps.getAndDelete(anyString())).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail("c-replay", TARGET_X, HUMAN_FRACTIONS, HUMAN_TIMES)));

        assertEquals(4001, ex.getCode());
        verify(redisOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void complete_ipMismatch_rejects() {
        String id = "c-ip";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(OTHER_IP), withTrail(id, TARGET_X, HUMAN_FRACTIONS, HUMAN_TIMES)));

        assertEquals(4001, ex.getCode());
        verify(redisOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void complete_wrongXWithinImageButOutsideTolerance_rejects() {
        String id = "c-x";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        int offBy = props.getSlider().getTolerancePx() + 1;
        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X - offBy, HUMAN_FRACTIONS, HUMAN_TIMES)));

        assertEquals(4001, ex.getCode());
        verify(redisOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void complete_xExactlyAtToleranceBoundary_passes() {
        // 边界应含等号：容差 6px 表示 |Δ|<=6 通过，避免实现写成 < 而悄悄收窄到 5px
        String id = "c-edge";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        service.complete(request(IP),
                withTrail(id, TARGET_X - props.getSlider().getTolerancePx(), HUMAN_FRACTIONS, HUMAN_TIMES));

        verify(redisOps).set(eq(RedisKeys.sliderOk(id)), eq("1"), any());
    }

    @Test
    void complete_missingX_rejects() {
        String id = "c-nullx";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));
        SliderCompleteRequest req = withTrail(id, TARGET_X, HUMAN_FRACTIONS, HUMAN_TIMES);
        req.setX(null);

        BizException ex = assertThrows(BizException.class, () -> service.complete(request(IP), req));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_tooFewTrailPoints_rejects() {
        String id = "c-few";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, new int[]{0, 50, 100}, new long[]{0, 150, 300})));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_trailFasterThanHumanlyPossible_rejects() {
        // 时长短于 minDurationMs → 判机器瞬移
        String id = "c-fast";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, HUMAN_FRACTIONS, new long[]{0, 10, 20, 30, 40, 50})));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_trailSlowerThanAllowed_rejects() {
        // 时长超过 maxDurationMs → 非人工（也防慢速重放）
        String id = "c-slow";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, HUMAN_FRACTIONS, new long[]{0, 9000, 18000, 27000, 36000, 45000})));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_uniformSpeedTrail_rejects() {
        // 匀速直线 = 机器特征：算出 x 后按固定速率直线拉过去。速度变异系数为 0，低于阈值。
        String id = "c-uniform";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, new int[]{0, 20, 40, 60, 80, 100},
                        new long[]{0, 60, 120, 180, 240, 300})));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_overshootThenCorrect_passes() {
        // 人手常见行为：拖过头再拉回来。轨迹早期回退必须被接受——曾经的"逐段回退即拒"
        // 实现会在这里误伤正常用户，这条用例是那个误伤的回归防线。
        String id = "c-overshoot";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        service.complete(request(IP), withTrail(id, TARGET_X, new int[]{0, 11, 33, 67, 106, 97, 100},
                new long[]{0, 40, 80, 130, 190, 240, 290}));

        verify(redisOps).set(eq(RedisKeys.sliderOk(id)), eq("1"), any());
    }

    @Test
    void complete_sawtoothTrail_rejects() {
        // 来回拉锯：总路径 736px vs 净位移 180px（约 4.1 倍），远超人手拖动的 1.0~1.5 倍。
        // 速度校验本身会放行（CV≈0.10 达标），所以拒绝必须来自路径/净位移比这一支。
        String id = "c-saw";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, new int[]{0, 83, 11, 89, 6, 100},
                        new long[]{0, 70, 130, 190, 250, 340})));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_trailWithNonIncreasingTimestamps_rejects() {
        String id = "c-time";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of(TARGET_X + ":" + IP));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, HUMAN_FRACTIONS, new long[]{0, 70, 70, 190, 250, 340})));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void complete_misshapenStoredValue_rejectsInsteadOfGuessing() {
        String id = "c-bad";
        when(redisOps.getAndDelete(RedisKeys.sliderPending(id))).thenReturn(Optional.of("garbage-without-separator"));

        BizException ex = assertThrows(BizException.class, () -> service.complete(
                request(IP), withTrail(id, TARGET_X, HUMAN_FRACTIONS, HUMAN_TIMES)));

        assertEquals(4001, ex.getCode());
    }

    // ==================== verifyAndConsume（发码前的消费） ====================

    @Test
    void verifyAndConsume_consumesMarkerExactlyOnce() {
        String id = "c-consume";
        when(redisOps.getAndDelete(RedisKeys.sliderOk(id))).thenReturn(Optional.of("1"));

        service.verifyAndConsume(id);

        verify(redisOps).getAndDelete(RedisKeys.sliderOk(id));
    }

    @Test
    void verifyAndConsume_withoutCompletedChallenge_rejects() {
        when(redisOps.getAndDelete(anyString())).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.verifyAndConsume("c-none"));

        assertEquals(4001, ex.getCode());
    }

    @Test
    void verifyAndConsume_blankChallengeId_rejects() {
        BizException ex = assertThrows(BizException.class, () -> service.verifyAndConsume("  "));

        assertEquals(4001, ex.getCode());
        verify(redisOps, never()).getAndDelete(anyString());
    }

    @Test
    void verifyAndConsume_disabled_passesThrough() {
        props.getSlider().setEnabled(false);

        service.verifyAndConsume(null);

        verifyNoInteractions(redisOps);
    }

    // ==================== 测试夹具 ====================

    /** 起步慢、中途快、末段减速——速度有明显起伏，是"人味"轨迹的判据。 */
    private static final int[] HUMAN_FRACTIONS = {0, 8, 30, 62, 88, 100};
    private static final long[] HUMAN_TIMES = {0, 70, 130, 190, 250, 340};

    private static MockHttpServletRequest request(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        return req;
    }

    /** 按目标距离的百分比造轨迹，终点恰为 targetX；fracs 单调则横向单调。 */
    private static SliderCompleteRequest withTrail(String id, int targetX, int[] fracs, long[] times) {
        SliderCompleteRequest req = new SliderCompleteRequest();
        req.setChallengeId(id);
        req.setX(targetX);
        List<SliderCompleteRequest.TrailPoint> trail = new ArrayList<>(fracs.length);
        for (int i = 0; i < fracs.length; i++) {
            SliderCompleteRequest.TrailPoint p = new SliderCompleteRequest.TrailPoint();
            p.setX(Math.round(targetX * fracs[i] / 100f));
            p.setT(times[i]);
            trail.add(p);
        }
        req.setTrail(trail);
        return req;
    }
}
