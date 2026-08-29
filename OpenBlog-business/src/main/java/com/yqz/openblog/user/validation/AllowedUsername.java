package com.yqz.openblog.user.validation;

/**
 * 用户名白名单：中英文（任意 Unicode 字母）、数字、下划线、点、短横线。
 * 排除空白、控制字符、零宽字符、emoji 等易致界面错乱或冒名的字符。
 */
public final class AllowedUsername {

    public static final String REGEXP = "^[\\p{L}\\p{N}_.-]{3,32}$";

    public static final String MESSAGE = "用户名仅支持中英文、数字、下划线、点、短横线，长度 3-32 位";

    private AllowedUsername() {
    }
}
