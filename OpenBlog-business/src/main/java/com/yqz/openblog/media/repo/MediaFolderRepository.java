package com.yqz.openblog.media.repo;

import com.yqz.openblog.media.entity.MediaFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaFolderRepository extends JpaRepository<MediaFolder, Long> {

    List<MediaFolder> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParentId(Long parentId);
}
