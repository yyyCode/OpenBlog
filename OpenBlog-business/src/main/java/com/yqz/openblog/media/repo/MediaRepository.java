package com.yqz.openblog.media.repo;

import com.yqz.openblog.media.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByStorageKey(String storageKey);
}

