package com.yqz.openblog.changelog.repo;

import com.yqz.openblog.changelog.entity.ChangelogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangelogRepository extends JpaRepository<ChangelogEntry, Long> {

    Page<ChangelogEntry> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
