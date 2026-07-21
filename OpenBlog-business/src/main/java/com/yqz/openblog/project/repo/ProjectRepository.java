package com.yqz.openblog.project.repo;

import com.yqz.openblog.project.entity.Project;
import com.yqz.openblog.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByStatusOrderBySortOrderAscPublishedAtDesc(ProjectStatus status, Pageable pageable);
}
