package com.yqz.openblog.user.repo;

import com.yqz.openblog.user.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    Optional<UserFollow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
    void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
}

