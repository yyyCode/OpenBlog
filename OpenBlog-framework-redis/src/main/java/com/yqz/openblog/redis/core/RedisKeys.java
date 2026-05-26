package com.yqz.openblog.redis.core;

/**
 * Redis Key 统一管理。
 * <p>
 * 命名规范：{@code openblog:{领域}:{实体}:{用途}:{参数...}}
 * <ul>
 *   <li>{@code content}  — 内容缓存（文章正文、列表）</li>
 *   <li>{@code counter}  — 计数去重（阅读量、访问量）</li>
 *   <li>{@code security} — 安全相关（登录锁定、滑块验证）</li>
 *   <li>{@code ratelimit}— 业务限流（反馈提交）</li>
 * </ul>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    // ==================== content ====================

    private static final String CONTENT_ARTICLE_BODY = "openblog:content:article:body";
    private static final String CONTENT_ARTICLE_LIST = "openblog:content:article:list";
    public static final String CONTENT_ARTICLE_LIST_VERSION = "openblog:content:article:list:version";

    // ==================== counter ====================

    private static final String COUNTER_ARTICLE_VIEW = "openblog:counter:article:view";
    private static final String COUNTER_SITE_VISIT = "openblog:counter:site:visit";

    // ==================== security ====================

    private static final String SECURITY_LOGIN_FAIL = "openblog:security:login:fail";
    private static final String SECURITY_LOGIN_LOCK = "openblog:security:login:lock";
    private static final String SECURITY_SLIDER_PENDING = "openblog:security:slider:pending";
    private static final String SECURITY_SLIDER_OK = "openblog:security:slider:ok";
    public static final String SECURITY_SLIDER_HEALTHCHECK = "openblog:security:slider:healthcheck";

    // ==================== ratelimit ====================

    private static final String RATELIMIT_FEEDBACK = "openblog:ratelimit:feedback";

    // ==================== builder methods ====================

    public static String articleBody(long articleId) {
        return CONTENT_ARTICLE_BODY + ":" + articleId;
    }

    public static String articleList(long version, Long categoryId, int page, int size) {
        return CONTENT_ARTICLE_LIST + ":v" + version + ":"
                + (categoryId == null ? "all" : categoryId) + ":" + page + ":" + size;
    }

    public static String articleView(long articleId, String clientIp) {
        return COUNTER_ARTICLE_VIEW + ":" + articleId + ":" + clientIp;
    }

    public static String siteVisit(String clientIp) {
        return COUNTER_SITE_VISIT + ":" + clientIp;
    }

    public static String loginFail(String ipKeySegment) {
        return SECURITY_LOGIN_FAIL + ":" + ipKeySegment;
    }

    public static String loginLock(String ipKeySegment) {
        return SECURITY_LOGIN_LOCK + ":" + ipKeySegment;
    }

    public static String sliderPending(String id) {
        return SECURITY_SLIDER_PENDING + ":" + id;
    }

    public static String sliderOk(String id) {
        return SECURITY_SLIDER_OK + ":" + id;
    }

    public static String feedbackIpDay(String ipKey, Object day) {
        return RATELIMIT_FEEDBACK + ":" + ipKey + ":" + day;
    }
}
