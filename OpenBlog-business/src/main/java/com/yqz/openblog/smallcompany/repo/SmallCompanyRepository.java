package com.yqz.openblog.smallcompany.repo;

import com.yqz.openblog.smallcompany.entity.SmallCompany;
import com.yqz.openblog.smallcompany.entity.SmallCompanyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmallCompanyRepository extends JpaRepository<SmallCompany, Long> {

    Page<SmallCompany> findByStatusOrderBySortOrderAscPublishedAtDesc(SmallCompanyStatus status, Pageable pageable);
}
