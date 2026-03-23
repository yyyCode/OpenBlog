package com.yqz.openblog.user.repo;

import com.yqz.openblog.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    Optional<User> findFirstByRoleOrderByIdAsc(com.yqz.openblog.user.entity.UserRole role);

    // 当存在多个 AUTHOR 时，优先展示最近更新的那位（避免前端一直显示旧作者信息）
    Optional<User> findFirstByRoleAndStatusOrderByUpdatedAtDesc(com.yqz.openblog.user.entity.UserRole role, String status);
}

