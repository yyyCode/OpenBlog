package com.yqz.openblog.smallcompany.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.smallcompany.dto.SmallCompanyDetailResponse;
import com.yqz.openblog.smallcompany.dto.SmallCompanyListItemResponse;
import com.yqz.openblog.smallcompany.dto.SmallCompanyUpsertRequest;
import com.yqz.openblog.smallcompany.entity.SmallCompany;
import com.yqz.openblog.smallcompany.entity.SmallCompanyStatus;
import com.yqz.openblog.smallcompany.repo.SmallCompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SmallCompanyService {

    private final SmallCompanyRepository repository;

    public SmallCompanyService(SmallCompanyRepository repository) {
        this.repository = repository;
    }

    public PageResult<SmallCompanyListItemResponse> listPublished(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<SmallCompany> pg = repository.findByStatusOrderBySortOrderAscPublishedAtDesc(
                SmallCompanyStatus.PUBLISHED, PageRequest.of(p, s));
        List<SmallCompanyListItemResponse> items = pg.getContent().stream().map(this::toListItem).toList();
        return new PageResult<>(items, pg.getNumber(), pg.getSize(), pg.getTotalElements());
    }

    public SmallCompanyDetailResponse detail(Long id) {
        SmallCompany c = repository.findById(id)
                .orElseThrow(() -> new BizException(4041, "公司不存在"));
        return toDetail(c);
    }

    public PageResult<SmallCompanyListItemResponse> listAll(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<SmallCompany> pg = repository.findAll(PageRequest.of(p, s));
        List<SmallCompanyListItemResponse> items = pg.getContent().stream().map(this::toListItem).toList();
        return new PageResult<>(items, pg.getNumber(), pg.getSize(), pg.getTotalElements());
    }

    @Transactional
    public SmallCompanyListItemResponse create(SmallCompanyUpsertRequest req) {
        SmallCompany c = new SmallCompany();
        apply(c, req);
        c = repository.save(c);
        return toListItem(c);
    }

    @Transactional
    public SmallCompanyListItemResponse update(Long id, SmallCompanyUpsertRequest req) {
        SmallCompany c = repository.findById(id)
                .orElseThrow(() -> new BizException(4041, "公司不存在"));
        apply(c, req);
        c = repository.save(c);
        return toListItem(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BizException(4041, "公司不存在");
        }
        repository.deleteById(id);
    }

    private void apply(SmallCompany c, SmallCompanyUpsertRequest req) {
        c.setName(req.getName().trim());
        c.setType(req.getType() != null ? req.getType().trim() : null);
        c.setScaleMin(req.getScaleMin());
        c.setScaleMax(req.getScaleMax());
        if (req.getScaleMin() != null && req.getScaleMax() != null
                && req.getScaleMax() < req.getScaleMin()) {
            throw new BizException(4002, "规模上限不能小于下限");
        }
        c.setColor(req.getColor() != null ? req.getColor().trim() : null);
        c.setLogoMediaKey(normalizeLogoMediaKey(req.getLogoMediaKey()));
        c.setCity(req.getCity() != null ? req.getCity().trim() : null);
        c.setFounded(req.getFounded());
        c.setAddress(req.getAddress() != null ? req.getAddress().trim() : null);
        c.setBusiness(req.getBusiness() != null ? req.getBusiness().trim() : null);
        c.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        c.setWebsite(req.getWebsite() != null ? req.getWebsite().trim() : null);
        c.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        if (req.getStatus() != null) {
            try {
                c.setStatus(SmallCompanyStatus.valueOf(req.getStatus().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BizException(4001, "status 仅支持 DRAFT / PUBLISHED");
            }
        }
        if (req.getPublishedAt() != null && !req.getPublishedAt().isBlank()) {
            c.setPublishedAt(Instant.parse(req.getPublishedAt()));
        } else if (c.getStatus() == SmallCompanyStatus.PUBLISHED && c.getPublishedAt() == null) {
            // 新建或「草稿转发布」统一补写发布时间；否则 published_at 为 NULL 会在
            // findByStatusOrderBySortOrderAscPublishedAtDesc 的 DESC 排序中沉底，新发布公司看不见
            c.setPublishedAt(Instant.now());
        }
    }

    /**
     * logo 字段兼容两种输入：裸 media key（general/xxx.png）或完整媒体链接（https://…/api/v1/media/files/general/xxx.png）。
     * 完整链接提取其中的 key 入库（DB 列仅 VARCHAR(64)，且前端 logoUrl() 会用 key 重新拼链接，不能存 URL）。
     * 非法输入（含 scheme/空白/路径穿越/超长）直接报错，避免静默丢数据。
     */
    private String normalizeLogoMediaKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        String marker = "/api/v1/media/files/";
        int idx = v.indexOf(marker);
        if (idx >= 0) {
            v = v.substring(idx + marker.length());
            int q = v.indexOf('?');
            if (q >= 0) {
                v = v.substring(0, q);
            }
        }
        if (v.length() > 64 || v.contains(" ") || v.startsWith("http") || v.contains("..")) {
            throw new BizException(4002, "logo 请填写媒体库 key（如 general/xxx.png）或本站媒体链接");
        }
        return v;
    }

    private SmallCompanyListItemResponse toListItem(SmallCompany c) {
        SmallCompanyListItemResponse r = new SmallCompanyListItemResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setType(c.getType());
        r.setScaleMin(c.getScaleMin());
        r.setScaleMax(c.getScaleMax());
        r.setColor(c.getColor());
        r.setLogoMediaKey(c.getLogoMediaKey());
        r.setCity(c.getCity());
        r.setFounded(c.getFounded());
        r.setSortOrder(c.getSortOrder());
        r.setStatus(c.getStatus());
        r.setPublishedAt(c.getPublishedAt());
        return r;
    }

    private SmallCompanyDetailResponse toDetail(SmallCompany c) {
        SmallCompanyDetailResponse r = new SmallCompanyDetailResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setType(c.getType());
        r.setScaleMin(c.getScaleMin());
        r.setScaleMax(c.getScaleMax());
        r.setColor(c.getColor());
        r.setLogoMediaKey(c.getLogoMediaKey());
        r.setCity(c.getCity());
        r.setFounded(c.getFounded());
        r.setAddress(c.getAddress());
        r.setBusiness(c.getBusiness());
        r.setDescription(c.getDescription());
        r.setWebsite(c.getWebsite());
        r.setSortOrder(c.getSortOrder());
        r.setStatus(c.getStatus());
        r.setPublishedAt(c.getPublishedAt());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        return r;
    }
}
