package com.yqz.openblog.audit.support;

import com.yqz.openblog.audit.model.AuditHttpInfo;
import com.yqz.openblog.audit.spi.AuditHttpInfoProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class DefaultAuditHttpInfoProvider implements AuditHttpInfoProvider {
    @Override
    public AuditHttpInfo currentHttp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        if (req == null) {
            return null;
        }
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = req.getRemoteAddr();
        }
        return new AuditHttpInfo(
                req.getMethod(),
                req.getRequestURI(),
                ip,
                req.getHeader("User-Agent")
        );
    }
}
