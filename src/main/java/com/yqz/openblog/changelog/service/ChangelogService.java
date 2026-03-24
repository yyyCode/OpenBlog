package com.yqz.openblog.changelog.service;

import com.yqz.openblog.changelog.dto.ChangelogDetailResponse;
import com.yqz.openblog.changelog.dto.ChangelogListItemResponse;
import com.yqz.openblog.changelog.dto.ChangelogUpsertRequest;
import com.yqz.openblog.changelog.entity.ChangelogEntry;
import com.yqz.openblog.changelog.repo.ChangelogRepository;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ChangelogService {

    private final ChangelogRepository changelogRepository;

    public ChangelogService(ChangelogRepository changelogRepository) {
        this.changelogRepository = changelogRepository;
    }

    public PageResult<ChangelogListItemResponse> listPublished(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<ChangelogEntry> pg = changelogRepository.findAllByOrderByPublishedAtDesc(PageRequest.of(p, s));
        List<ChangelogListItemResponse> items = pg.getContent().stream().map(this::toListItem).toList();
        return new PageResult<>(items, pg.getNumber(), pg.getSize(), pg.getTotalElements());
    }

    public ChangelogDetailResponse detail(Long id) {
        ChangelogEntry e = changelogRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "更新日志不存在"));
        return toDetail(e);
    }

    @Transactional
    public ChangelogListItemResponse create(ChangelogUpsertRequest req) {
        ChangelogEntry e = new ChangelogEntry();
        apply(e, req);
        e = changelogRepository.save(e);
        return toListItem(e);
    }

    @Transactional
    public ChangelogListItemResponse update(Long id, ChangelogUpsertRequest req) {
        ChangelogEntry e = changelogRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "更新日志不存在"));
        apply(e, req);
        e = changelogRepository.save(e);
        return toListItem(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!changelogRepository.existsById(id)) {
            throw new BizException(4041, "更新日志不存在");
        }
        changelogRepository.deleteById(id);
    }

    private void apply(ChangelogEntry e, ChangelogUpsertRequest req) {
        e.setTitle(req.getTitle().trim());
        e.setVersionLabel(req.getVersionLabel() != null ? req.getVersionLabel().trim() : null);
        e.setContentMarkdown(req.getContentMarkdown());
        if (req.getPublishedAt() != null && !req.getPublishedAt().isBlank()) {
            e.setPublishedAt(Instant.parse(req.getPublishedAt()));
        } else if (e.getId() == null) {
            e.setPublishedAt(Instant.now());
        }
    }

    private ChangelogListItemResponse toListItem(ChangelogEntry e) {
        ChangelogListItemResponse r = new ChangelogListItemResponse();
        r.setId(e.getId());
        r.setTitle(e.getTitle());
        r.setVersionLabel(e.getVersionLabel());
        r.setPublishedAt(e.getPublishedAt());
        return r;
    }

    private ChangelogDetailResponse toDetail(ChangelogEntry e) {
        ChangelogDetailResponse r = new ChangelogDetailResponse();
        r.setId(e.getId());
        r.setTitle(e.getTitle());
        r.setVersionLabel(e.getVersionLabel());
        r.setContentMarkdown(e.getContentMarkdown());
        r.setPublishedAt(e.getPublishedAt());
        return r;
    }
}
