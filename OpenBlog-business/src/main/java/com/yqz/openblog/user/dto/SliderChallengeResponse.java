package com.yqz.openblog.user.dto;

/**
 * 滑动验证：拉取 challengeId 与图片，拖动完成后调用 {@code /auth/slider-complete} 提交落点与轨迹。
 * <p>
 * {@code enabled=false} 时其余字段均为 null，前端据此不渲染滑块直接发码。
 * 缺口真值坐标<b>不在响应中</b>——只存服务端 Redis，客户端需从底图像素自行解出。
 */
public class SliderChallengeResponse {

    private boolean enabled;
    private String challengeId;
    /** 带缺口的底图，data URI。 */
    private String background;
    /** 滑块块图，data URI。 */
    private String slider;
    /** 滑块块的初始纵坐标（缺口 y 与之一致，故纵向无需拖动）。 */
    private Integer sliderY;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getSlider() {
        return slider;
    }

    public void setSlider(String slider) {
        this.slider = slider;
    }

    public Integer getSliderY() {
        return sliderY;
    }

    public void setSliderY(Integer sliderY) {
        this.sliderY = sliderY;
    }
}
