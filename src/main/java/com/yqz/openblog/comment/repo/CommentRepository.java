package com.yqz.openblog.comment.repo;

import com.yqz.openblog.comment.entity.Comment;
import com.yqz.openblog.comment.entity.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByArticleIdAndStatus(Long articleId, CommentStatus status, Pageable pageable);

    Optional<Comment> findByIdAndStatus(Long id, CommentStatus status);

    List<Comment> findByIdInAndStatus(List<Long> ids, CommentStatus status);
}

