package com.yqz.openblog.feedback.repo;

import com.yqz.openblog.feedback.entity.FeedbackEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface FeedbackRepository extends JpaRepository<FeedbackEntry, Long> {
    boolean existsByIpKeyAndSubmitDay(String ipKey, LocalDate submitDay);

    Page<FeedbackEntry> findAllByStatusOrderByCreatedAtDesc(FeedbackEntry.Status status, Pageable pageable);
}

