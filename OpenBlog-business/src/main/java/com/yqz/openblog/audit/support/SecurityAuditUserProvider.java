package com.yqz.openblog.audit.support;

import com.yqz.openblog.audit.spi.AuditUserProvider;
import com.yqz.openblog.audit.model.AuditUser;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditUserProvider implements AuditUserProvider {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    public SecurityAuditUserProvider(CurrentUser currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    @Override
    public AuditUser currentUser() {
        Long uid = currentUser.userId();
        if (uid == null) {
            return new AuditUser(null, "anonymous");
        }
        User u = userRepository.findById(uid).orElse(null);
        String username = u != null ? u.getUsername() : uid.toString();
        return new AuditUser(uid.toString(), username);
    }
}
