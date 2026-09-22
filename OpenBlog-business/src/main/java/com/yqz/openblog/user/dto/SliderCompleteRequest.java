package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 滑动验证提交：落点 x 与拖动轨迹。
 * <p>
 * {@code x} 用包装类型而非 {@code int}：缺字段时得到 4001「请先完成滑动验证」，
 * 与其它滑块失败同码，而不是落到参数校验的通用错误码上。轨迹各字段同理。
 */
public class SliderCompleteRequest {

    @NotBlank
    private String challengeId;

    /** 滑块最终横向落点（像素，原点为底图左边缘）。 */
    private Integer x;

    /** 拖动采样轨迹，按时间递增。用于识别"算出答案后瞬移过去"的机器行为。 */
    private List<TrailPoint> trail;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    public List<TrailPoint> getTrail() {
        return trail;
    }

    public void setTrail(List<TrailPoint> trail) {
        this.trail = trail;
    }

    /** 轨迹采样点：{@code t} 为相对拖动开始的毫秒偏移。 */
    public static class TrailPoint {

        private Integer x;
        private Long t;

        public Integer getX() {
            return x;
        }

        public void setX(Integer x) {
            this.x = x;
        }

        public Long getT() {
            return t;
        }

        public void setT(Long t) {
            this.t = t;
        }
    }
}
