package com.yqz.openblog.user.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 邮箱校验器：拦截一次性/临时邮箱 + 常见拼写错误提示。
 */
@Component
public class EmailValidator {

    private static final Logger log = LoggerFactory.getLogger(EmailValidator.class);

    /** 一次性/临时邮箱域名黑名单 */
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com", "mailinator.net", "mailinator.org",
            "10minutemail.com", "10minutemail.net",
            "guerrillamail.com", "guerrillamail.net", "guerrillamail.org",
            "temp-mail.org", "temp-mail.com", "temp-mail.net",
            "trashmail.com", "trashmail.net",
            "yopmail.com", "yopmail.net",
            "sharklasers.com", "getnada.com",
            "throwaway.email", "dispostable.com",
            "mailnesia.com", "spam4.me",
            "fakeinbox.com", "tempmail.com",
            "moakt.com", "moakt.cc",
            "dropmail.me", "mytemp.email",
            "emailondeck.com", "33mail.com",
            "anonaddy.com", "simplelogin.com",
            "bugmenot.com", "spambox.us",
            "spamgourmet.com", "mailexpire.com",
            "deadaddress.com", "discard.email",
            "dont-register.com", "etempmail.net",
            "eyepaste.com", "grr.la",
            "hidemyass.com", "incognitomail.com",
            "mailcatch.com", "maildrop.cc",
            "mailmetrash.com", "mailnull.com",
            "mintemail.com", "mytrashmail.com",
            "nwytg.com", "objectmail.com",
            "oneoffemail.com", "owlymail.com",
            "pookmail.com", "sneakemail.com",
            "spamdecoy.net", "spamex.com",
            "temporarymail.com", "tmpmail.org",
            "trash2009.com", "wh4f.org",
            "wuzup.net", "zippymail.info"
    );

    /** 常见拼写错误 → 正确域名 */
    private static final Map<String, String> TYPO_CORRECTIONS = Map.ofEntries(
            Map.entry("gamil.com", "gmail.com"),
            Map.entry("gmal.com", "gmail.com"),
            Map.entry("gmial.com", "gmail.com"),
            Map.entry("gnail.com", "gmail.com"),
            Map.entry("gmail.con", "gmail.com"),
            Map.entry("gmail.co", "gmail.com"),
            Map.entry("gmail.cm", "gmail.com"),
            Map.entry("gmail.om", "gmail.com"),
            Map.entry("hotmai.com", "hotmail.com"),
            Map.entry("hotmal.com", "hotmail.com"),
            Map.entry("hotmail.con", "hotmail.com"),
            Map.entry("hotmail.co", "hotmail.com"),
            Map.entry("outloo.com", "outlook.com"),
            Map.entry("outlok.com", "outlook.com"),
            Map.entry("outlook.con", "outlook.com"),
            Map.entry("outlook.co", "outlook.com"),
            Map.entry("yahooo.com", "yahoo.com"),
            Map.entry("yaho.com", "yahoo.com"),
            Map.entry("yahoo.con", "yahoo.com"),
            Map.entry("163.con", "163.com"),
            Map.entry("qq.con", "qq.com"),
            Map.entry("qq.cm", "qq.com"),
            Map.entry("foxmail.con", "foxmail.com")
    );

    /**
     * 校验邮箱，返回错误消息（null = 通过）。
     */
    public String validate(String email) {
        if (email == null || email.isBlank()) {
            return "邮箱不能为空";
        }

        String lower = email.toLowerCase().trim();

        // 1. 基本格式
        int atIndex = lower.indexOf('@');
        if (atIndex <= 0 || atIndex == lower.length() - 1) {
            return "邮箱格式不正确";
        }

        String domain = lower.substring(atIndex + 1);

        // 2. 一次性/临时邮箱
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            log.warn("Blocked disposable email domain: {}", domain);
            return "不支持临时邮箱注册，请使用常用邮箱";
        }

        // 3. 拼写错误提示
        String correction = TYPO_CORRECTIONS.get(domain);
        if (correction != null) {
            String suggested = lower.substring(0, atIndex + 1) + correction;
            return "邮箱拼写可能有误，您想输入的是 " + suggested + " 吗？";
        }

        // 4. 拒绝 + 别名（常见攻击手段：同一邮箱无限注册）
        if (lower.indexOf('+', 0) < atIndex && lower.charAt(lower.indexOf('+')) == '+') {
            return "不支持带 + 别名的邮箱地址";
        }

        return null;
    }
}
