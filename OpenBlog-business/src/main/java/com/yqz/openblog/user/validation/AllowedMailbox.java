package com.yqz.openblog.user.validation;

/**
 * 前台注册 / 修改资料：邮箱域名白名单（QQ、网易、谷歌系）。
 */
public final class AllowedMailbox {

    /**
     * 本地部分 + 允许的域名（不区分大小写）。不含国际域名邮箱。
     */
    public static final String REGEXP =
            "(?i)^[a-zA-Z0-9._%+-]+@(qq\\.com|foxmail\\.com|163\\.com|126\\.com|yeah\\.net|gmail\\.com|googlemail\\.com)$";

    /** 允许“不修改邮箱”时传空串 */
    public static final String REGEXP_OPTIONAL =
            "(?i)^$|^[a-zA-Z0-9._%+-]+@(qq\\.com|foxmail\\.com|163\\.com|126\\.com|yeah\\.net|gmail\\.com|googlemail\\.com)$";

    public static final String MESSAGE =
            "仅支持 QQ 邮箱（@qq.com、@foxmail.com）、网易邮箱（@163.com、@126.com、@yeah.net）或谷歌邮箱（@gmail.com）";

    private AllowedMailbox() {
    }
}
