package com.yqz.openblog.notification;

import com.yqz.openblog.common.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTemplateServiceTest {

    private final NotificationTemplateService templateService = new NotificationTemplateService();

    @Test
    void rendersCodeIntoVerificationTemplate() {
        String html = templateService.render(
                NotificationTemplateService.REGISTER_VERIFICATION_CODE, Map.of("code", "123456"));

        assertTrue(html.contains("123456"), "验证码应渲染进正文");
        assertFalse(html.contains("{{code}}"), "占位符应被替换");
        assertTrue(html.contains("OpenBlog 注册验证码"), "模板骨架应保留");
    }

    @Test
    void missingParamLeavesPlaceholder() {
        String html = templateService.render(NotificationTemplateService.REGISTER_VERIFICATION_CODE, Map.of());
        assertTrue(html.contains("{{code}}"), "缺参时保留占位符（fail-open，便于发现模板参数缺失）");
    }

    @Test
    void unknownTemplateFailsClosed() {
        BizException e = assertThrows(BizException.class,
                () -> templateService.render("no-such-template", Map.of()));
        assertEquals(4000, e.getCode());
    }

    @Test
    void blankTemplateFailsClosed() {
        assertThrows(BizException.class, () -> templateService.render("  ", Map.of()));
    }
}
