package com.yqz.openblog.notification;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.NotificationRpcService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通知模板渲染：templateCode + params → 各渠道内容（邮件 HTML / 短信文本 / 飞书 JSON）。
 * <p>
 * 本期内置「注册验证码」邮件模板，占位符 {{key}} 替换；未来可扩展为配置化 / DB 多模板，
 * 调用方无感。这是通知抽象层「开放性」的落点之一。
 */
@Service
public class NotificationTemplateService {

    /** 注册验证码模板 code（与共享契约同源，见 NotificationRpcService.TEMPLATE_REGISTER_VERIFICATION_CODE）。 */
    public static final String REGISTER_VERIFICATION_CODE = NotificationRpcService.TEMPLATE_REGISTER_VERIFICATION_CODE;

    /** 找回密码验证码模板 code（与共享契约同源，见 NotificationRpcService.TEMPLATE_RESET_VERIFICATION_CODE）。 */
    public static final String RESET_VERIFICATION_CODE = NotificationRpcService.TEMPLATE_RESET_VERIFICATION_CODE;

    private static final Map<String, String> TEMPLATES = Map.of(
            REGISTER_VERIFICATION_CODE,
            "<div style=\"max-width:480px;margin:0 auto;font-family:'Microsoft YaHei',Arial,sans-serif;"
            + "padding:24px;border:1px solid #e5e5e5;border-radius:8px;color:#333;\">"
            + "<h2 style=\"margin:0 0 16px;font-size:20px;\">OpenBlog 注册验证码</h2>"
            + "<p style=\"margin:0 0 8px;font-size:14px;color:#666;\">你正在注册 OpenBlog 账号，以下是你的验证码：</p>"
            + "<p style=\"margin:0 0 16px;font-size:32px;letter-spacing:6px;font-weight:700;color:#1a73e8;\">{{code}}</p>"
            + "<p style=\"margin:0;font-size:13px;color:#999;\">验证码 5 分钟内有效，请勿泄露给他人。"
            + "若非本人操作请忽略本邮件。</p>"
            + "</div>",
            RESET_VERIFICATION_CODE,
            "<div style=\"max-width:480px;margin:0 auto;font-family:'Microsoft YaHei',Arial,sans-serif;"
            + "padding:24px;border:1px solid #e5e5e5;border-radius:8px;color:#333;\">"
            + "<h2 style=\"margin:0 0 16px;font-size:20px;\">OpenBlog 找回密码验证码</h2>"
            + "<p style=\"margin:0 0 8px;font-size:14px;color:#666;\">你正在重置 OpenBlog 账号密码，以下是你的验证码：</p>"
            + "<p style=\"margin:0 0 16px;font-size:32px;letter-spacing:6px;font-weight:700;color:#1a73e8;\">{{code}}</p>"
            + "<p style=\"margin:0;font-size:13px;color:#999;\">验证码 5 分钟内有效，请勿泄露给他人。"
            + "若非本人操作请忽略本邮件，密码不会因此改变。</p>"
            + "</div>"
    );

    /**
     * 渲染通知内容：将模板中的 {{key}} 占位符替换为 params 中的值。
     * 未知模板抛 BizException(4000)，fail-closed。
     */
    public String render(String templateCode, Map<String, Object> params) {
        if (templateCode == null || templateCode.isBlank()) {
            throw new BizException(4000, "通知模板不能为空");
        }
        String template = TEMPLATES.get(templateCode);
        if (template == null) {
            throw new BizException(4000, "未知的通知模板: " + templateCode);
        }

        String content = template;
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                content = content.replace("{{" + e.getKey() + "}}", e.getValue().toString());
            }
        }
        return content;
    }
}
