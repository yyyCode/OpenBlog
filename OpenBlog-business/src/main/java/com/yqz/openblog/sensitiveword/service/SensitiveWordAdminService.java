package com.yqz.openblog.sensitiveword.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.audit.annotation.AuditLog;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.config.SensitiveWordProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.sensitiveword.dto.SensitiveWordDtos;
import com.yqz.openblog.sensitiveword.entity.SensitiveWord;
import com.yqz.openblog.sensitiveword.entity.SensitiveWordChangeLog;
import com.yqz.openblog.sensitiveword.entity.SensitiveWordVersion;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordChangeLogMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordVersionMapper;
import com.yqz.openblog.sensitiveword.runtime.FilterResult;
import com.yqz.openblog.sensitiveword.runtime.SensitiveWordManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 敏感词管理工作集：CRUD、待发布差异、导入导出、在线测试、状态。 */
@Service
public class SensitiveWordAdminService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordAdminService.class);

    private static final int QUERY_CHUNK = 500;
    private static final int ITEM_SAMPLE_LIMIT = 200;

    private final SensitiveWordMapper wordMapper;
    private final SensitiveWordVersionMapper versionMapper;
    private final SensitiveWordChangeLogMapper changeLogMapper;
    private final SensitiveWordStore store;
    private final SensitiveWordManager manager;
    private final SensitiveWordProperties properties;
    private final CurrentUser currentUser;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SensitiveWordAdminService(SensitiveWordMapper wordMapper,
                                     SensitiveWordVersionMapper versionMapper,
                                     SensitiveWordChangeLogMapper changeLogMapper,
                                     SensitiveWordStore store,
                                     SensitiveWordManager manager,
                                     SensitiveWordProperties properties,
                                     CurrentUser currentUser,
                                     StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper) {
        this.wordMapper = wordMapper;
        this.versionMapper = versionMapper;
        this.changeLogMapper = changeLogMapper;
        this.store = store;
        this.manager = manager;
        this.properties = properties;
        this.currentUser = currentUser;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== 词条 CRUD ====================

    public PageResult<SensitiveWordDtos.ItemResponse> list(String keyword, String category,
                                                           Integer status, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(200, Math.max(1, size));
        LambdaQueryWrapper<SensitiveWord> q = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            q.like(SensitiveWord::getWord, keyword.trim());
        }
        if (category != null && !category.isBlank()) {
            q.eq(SensitiveWord::getCategory, category.trim());
        }
        if (status != null) {
            q.eq(SensitiveWord::getStatus, status);
        }
        q.orderByDesc(SensitiveWord::getUpdatedAt).orderByAsc(SensitiveWord::getId);
        IPage<SensitiveWord> result = wordMapper.selectPage(new Page<>(p + 1L, s), q);
        List<SensitiveWordDtos.ItemResponse> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(items, p, s, result.getTotal());
    }

    public List<String> categories() {
        return wordMapper.selectCategories();
    }

    @Transactional
    @AuditLog(action = "SENSITIVE_WORD_CREATE", entityType = "SensitiveWord")
    public Long create(SensitiveWordDtos.UpsertRequest req) {
        String word = normalizeWord(req.word());
        if (wordMapper.countByWord(word) > 0) {
            throw new BizException(4091, "词条已存在");
        }
        Instant now = Instant.now();
        String operator = operator();
        SensitiveWord e = new SensitiveWord();
        e.setWord(word);
        e.setCategory(normalizeCategory(req.category()));
        e.setStatus(normalizeStatus(req.status()));
        e.setRemark(req.remark());
        e.setCreatedBy(operator);
        e.setUpdatedBy(operator);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        try {
            wordMapper.insert(e);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            throw new BizException(4091, "词条已存在");
        }
        recordChange(word, "ADD", null, toMap(e));
        return e.getId();
    }

    @Transactional
    @AuditLog(action = "SENSITIVE_WORD_UPDATE", entityType = "SensitiveWord", entityId = "#id")
    public void update(Long id, SensitiveWordDtos.UpsertRequest req) {
        SensitiveWord e = wordMapper.selectById(id);
        if (e == null) {
            throw new BizException(4041, "词条不存在");
        }
        Map<String, Object> before = toMap(e);
        String word = normalizeWord(req.word());
        if (!word.equals(e.getWord()) && wordMapper.countByWordExcludingId(word, id) > 0) {
            throw new BizException(4091, "词条已存在");
        }
        e.setWord(word);
        e.setCategory(normalizeCategory(req.category()));
        e.setStatus(normalizeStatus(req.status()));
        e.setRemark(req.remark());
        e.setUpdatedBy(operator());
        e.setUpdatedAt(Instant.now());
        try {
            wordMapper.updateById(e);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            throw new BizException(4091, "词条已存在");
        }
        recordChange(word, "UPDATE", before, toMap(e));
    }

    @Transactional
    @AuditLog(action = "SENSITIVE_WORD_DELETE", entityType = "SensitiveWord", entityId = "#id")
    public void delete(Long id) {
        SensitiveWord e = wordMapper.selectById(id);
        if (e == null) {
            throw new BizException(4041, "词条不存在");
        }
        wordMapper.deleteById(id);
        recordChange(e.getWord(), "DELETE", toMap(e), null);
    }

    @Transactional
    @AuditLog(action = "SENSITIVE_WORD_BATCH_DELETE", entityType = "SensitiveWord")
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (ids.size() > 500) {
            throw new BizException(4001, "单次最多删除 500 条");
        }
        List<SensitiveWord> rows = wordMapper.selectBatchIds(ids);
        if (rows.isEmpty()) {
            return 0;
        }
        wordMapper.deleteBatchIds(ids);
        for (SensitiveWord e : rows) {
            recordChange(e.getWord(), "DELETE", toMap(e), null);
        }
        return rows.size();
    }

    // ==================== 待发布 / 版本 ====================

    public SensitiveWordDtos.PendingResponse pending() {
        SensitiveWordVersion latest = store.latestPublished();
        Set<String> current = new LinkedHashSet<>(store.enabledWords());
        Set<String> old = latest == null ? Set.of() : new HashSet<>(store.wordsOfVersion(latest));
        List<String> added = current.stream().filter(w -> !old.contains(w)).toList();
        List<String> removed = old.stream().filter(w -> !current.contains(w)).toList();
        long edited = latest == null ? 0 : store.editedSince(latest.getPublishedAt());
        int updated = (int) Math.max(0, edited - added.size());

        List<SensitiveWordDtos.PendingItem> items = new ArrayList<>();
        for (String w : added) {
            if (items.size() >= ITEM_SAMPLE_LIMIT) {
                break;
            }
            items.add(new SensitiveWordDtos.PendingItem("ADD", w));
        }
        for (String w : removed) {
            if (items.size() >= ITEM_SAMPLE_LIMIT) {
                break;
            }
            items.add(new SensitiveWordDtos.PendingItem("DELETE", w));
        }
        return new SensitiveWordDtos.PendingResponse(added.size(), removed.size(), updated, items);
    }

    public PageResult<SensitiveWordDtos.VersionResponse> versions(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<SensitiveWordVersion> q = Wrappers.lambdaQuery();
        q.select(SensitiveWordVersion.class, info -> !"snapshot_blob".equals(info.getColumn()))
                .orderByDesc(SensitiveWordVersion::getId);
        IPage<SensitiveWordVersion> result = versionMapper.selectPage(new Page<>(p + 1L, s), q);
        List<SensitiveWordDtos.VersionResponse> items = result.getRecords().stream()
                .map(v -> new SensitiveWordDtos.VersionResponse(
                        v.getId(), v.getKind(), v.getStatus(), v.getSourceVersion(), v.getWordCount(),
                        v.getChecksum(), v.getChangeSummary(), v.getRemark(), v.getCreatedBy(),
                        v.getPublishedAt()))
                .toList();
        return new PageResult<>(items, p, s, result.getTotal());
    }

    public SensitiveWordDtos.VersionDiffResponse versionDiff(long version) {
        SensitiveWordVersion target = versionMapper.selectById(version);
        if (target == null) {
            throw new BizException(4041, "版本不存在");
        }
        Set<String> old = new HashSet<>(store.wordsOfVersion(target));
        Set<String> current = new LinkedHashSet<>(store.enabledWords());
        List<String> added = current.stream().filter(w -> !old.contains(w)).limit(ITEM_SAMPLE_LIMIT).toList();
        List<String> removed = old.stream().filter(w -> !current.contains(w)).limit(ITEM_SAMPLE_LIMIT).toList();
        int addedCount = (int) current.stream().filter(w -> !old.contains(w)).count();
        int removedCount = (int) old.stream().filter(w -> !current.contains(w)).count();
        return new SensitiveWordDtos.VersionDiffResponse(version, addedCount, removedCount, added, removed);
    }

    // ==================== 导入 / 导出 ====================

    public SensitiveWordDtos.ImportPreviewResponse previewImport(MultipartFile file) {
        Parsed parsed = parse(file);
        Set<String> existing = existingWords(parsed.validWords());
        int newCount = 0;
        int exist = 0;
        int invalid = 0;
        int ignored = 0;
        List<SensitiveWordDtos.ImportRow> rows = new ArrayList<>(parsed.rows().size());
        for (SensitiveWordDtos.ImportRow row : parsed.rows()) {
            String status = row.status();
            if ("OK".equals(status)) {
                status = existing.contains(row.word()) ? "EXIST" : "NEW";
                if ("EXIST".equals(status)) {
                    exist++;
                } else {
                    newCount++;
                }
            } else if ("INVALID".equals(status)) {
                invalid++;
            } else if ("IGNORED".equals(status)) {
                ignored++;
            }
            rows.add(new SensitiveWordDtos.ImportRow(row.line(), row.word(), status, row.reason()));
        }
        List<SensitiveWordDtos.ImportRow> sample = rows.size() > ITEM_SAMPLE_LIMIT
                ? rows.subList(0, ITEM_SAMPLE_LIMIT) : rows;
        return new SensitiveWordDtos.ImportPreviewResponse(
                parsed.total(), newCount, exist, invalid, ignored, sample);
    }

    @Transactional
    @AuditLog(action = "SENSITIVE_WORD_IMPORT", entityType = "SensitiveWord")
    public SensitiveWordDtos.ImportResult importFile(MultipartFile file, String mode, boolean confirmReplace) {
        boolean replace = "REPLACE".equalsIgnoreCase(mode);
        if (replace && !confirmReplace) {
            throw new BizException(4000, "全量替换需要二次确认");
        }
        Parsed parsed = parse(file);
        Set<String> existing = existingWords(parsed.validWords());
        List<String> words = parsed.rows().stream()
                .filter(r -> "OK".equals(r.status()))
                .map(SensitiveWordDtos.ImportRow::word)
                .distinct()
                .toList();
        List<String> added = words.stream().filter(w -> !existing.contains(w)).toList();
        int invalid = (int) parsed.rows().stream().filter(r -> "INVALID".equals(r.status())).count();
        int skipped = replace ? 0 : words.size() - added.size();

        String operator = operator();
        long beforeCount = wordMapper.selectCount(null);
        List<String> removed = List.of();
        if (replace) {
            Set<String> incoming = new HashSet<>(words);
            removed = wordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                            .select(SensitiveWord::getWord))
                    .stream().map(SensitiveWord::getWord)
                    .filter(w -> !incoming.contains(w))
                    .toList();
            wordMapper.deleteAll();
        }
        insertWords(words, operator);
        long afterCount = wordMapper.selectCount(null);
        int inserted = (int) Math.max(0, afterCount - (replace ? 0 : beforeCount));

        // 按词记录变更，发布时会挂到对应版本；REPLACE 下删除的词也要留痕
        recordImportChanges(replace ? words : added, "ADD", operator);
        recordImportChanges(removed, "DELETE", operator);

        SensitiveWordChangeLog change = new SensitiveWordChangeLog();
        change.setWord("*");
        change.setAction("IMPORT");
        change.setAfterJson("{\"mode\":\"" + (replace ? "REPLACE" : "APPEND")
                + "\",\"inserted\":" + inserted + ",\"invalid\":" + invalid + "}");
        change.setOperator(operator);
        change.setOperatedAt(Instant.now());
        changeLogMapper.insert(change);

        log.info("敏感词导入完成：mode={}, inserted={}, skipped={}, invalid={}, operator={}",
                replace ? "REPLACE" : "APPEND", inserted, skipped, invalid, operator);
        return new SensitiveWordDtos.ImportResult(inserted, skipped, invalid, inserted > 0);
    }

    public byte[] exportCsv() {
        List<SensitiveWord> rows = wordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                .orderByAsc(SensitiveWord::getWord));
        StringBuilder sb = new StringBuilder(rows.size() * 48);
        sb.append('﻿');
        sb.append("word,category,status,remark,updated_by,updated_at\n");
        for (SensitiveWord e : rows) {
            sb.append(csv(e.getWord())).append(',')
                    .append(csv(e.getCategory())).append(',')
                    .append(e.getStatus()).append(',')
                    .append(csv(e.getRemark())).append(',')
                    .append(csv(e.getUpdatedBy())).append(',')
                    .append(e.getUpdatedAt() == null ? "" : e.getUpdatedAt()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 测试 / 状态 ====================

    public SensitiveWordDtos.CheckResponse check(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException(4001, "测试文本不能为空");
        }
        if (text.length() > 20_000) {
            throw new BizException(4001, "测试文本最长 20000 字");
        }
        long start = System.nanoTime();
        FilterResult result = manager.filter(text);
        long costMs = (System.nanoTime() - start) / 1_000_000;
        return new SensitiveWordDtos.CheckResponse(
                result.hit(), result.filteredText(), result.hitWords(),
                manager.localVersion(), manager.engine(), costMs);
    }

    public SensitiveWordDtos.StatusResponse status() {
        SensitiveWordVersion latest = store.latestPublished();
        SensitiveWordDtos.PendingResponse pending = pending();
        SensitiveWordDtos.ShadowStatus shadow = null;
        if (!"none".equals(manager.shadowEngine())) {
            shadow = new SensitiveWordDtos.ShadowStatus(
                    manager.shadowEngine(), manager.shadowSamples(), manager.shadowMismatches(),
                    round2(manager.primaryAvgUs()), round2(manager.shadowAvgUs()));
        }
        long latestVersion = latest == null ? manager.localVersion() : latest.getId();
        return new SensitiveWordDtos.StatusResponse(
                manager.state(),
                manager.engine(),
                latest == null ? null : latest.getId(),
                manager.localVersion(),
                manager.wordCount(),
                latest == null ? null : latest.getPublishedAt(),
                pending,
                nodeStatus(latestVersion),
                shadow);
    }

    /** @param latestVersion 全局最新已发布版本；不能用本节点版本做基准，否则落后节点会把自己报成最新 */
    private SensitiveWordDtos.NodeStatus nodeStatus(long latestVersion) {
        String expected = String.valueOf(latestVersion);
        List<String> stale = new ArrayList<>();
        int total = 0;
        int upToDate = 0;
        try {
            Set<String> keys = redisTemplate.keys(RedisKeys.CONTENT_SENSITIVE_WORD_NODE_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    total++;
                    String value = redisTemplate.opsForValue().get(key);
                    String id = key.substring(RedisKeys.CONTENT_SENSITIVE_WORD_NODE_PREFIX.length());
                    if (expected.equals(value)) {
                        upToDate++;
                    } else {
                        stale.add(id + "@" + (value == null ? "?" : value));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取敏感词节点状态失败", e);
        }
        return new SensitiveWordDtos.NodeStatus(total, upToDate, stale);
    }

    // ==================== 内部 ====================

    private Parsed parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(4001, "请选择导入文件");
        }
        if (file.getSize() > properties.getImportConfig().getMaxFileSize().toBytes()) {
            throw new BizException(4002, "导入文件超过大小限制");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(4002, "读取导入文件失败");
        }
        if (containsNul(bytes)) {
            throw new BizException(4002, "不支持二进制文件，请上传 UTF-8 文本");
        }
        String content;
        try {
            // 严格解码：非法 UTF-8 直接拒绝，避免用替换字符生成乱码词条
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new BizException(4002, "文件不是合法的 UTF-8 文本");
        }
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        boolean csv = isCsv(file);
        boolean headerConsumed = false;
        String[] lines = content.split("\\R", -1);
        Set<String> seen = new HashSet<>();
        List<SensitiveWordDtos.ImportRow> rows = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            if (csv) {
                // 导出的 CSV 可直接回导：只取第一列，并跳过表头
                raw = firstCsvField(raw);
                if (!headerConsumed) {
                    headerConsumed = true;
                    if ("word".equalsIgnoreCase(raw)) {
                        continue;
                    }
                }
                if (raw.isEmpty()) {
                    continue;
                }
            }
            total++;
            if (total > properties.getImportConfig().getMaxRows()) {
                throw new BizException(4002, "导入行数超过限制");
            }
            String reason = validateWord(raw);
            if (reason != null) {
                rows.add(new SensitiveWordDtos.ImportRow(i + 1, raw, "INVALID", reason));
            } else if (!seen.add(raw)) {
                rows.add(new SensitiveWordDtos.ImportRow(i + 1, raw, "IGNORED", "文件内重复"));
            } else {
                rows.add(new SensitiveWordDtos.ImportRow(i + 1, raw, "OK", null));
            }
        }
        return new Parsed(rows, total);
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCsv(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    /** 取 CSV 第一列，支持双引号包裹与 "" 转义。 */
    private static String firstCsvField(String line) {
        if (!line.startsWith("\"")) {
            int comma = line.indexOf(',');
            return (comma < 0 ? line : line.substring(0, comma)).trim();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private Set<String> existingWords(Collection<String> words) {
        Set<String> found = new HashSet<>();
        List<String> all = new ArrayList<>(words);
        for (int i = 0; i < all.size(); i += QUERY_CHUNK) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + QUERY_CHUNK));
            wordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                            .select(SensitiveWord::getWord)
                            .in(SensitiveWord::getWord, chunk))
                    .forEach(w -> found.add(w.getWord()));
        }
        return found;
    }

    private void recordImportChanges(List<String> words, String action, String operator) {
        if (words.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<SensitiveWordChangeLog> batch = new ArrayList<>(1000);
        for (String word : words) {
            SensitiveWordChangeLog change = new SensitiveWordChangeLog();
            change.setWord(word);
            change.setAction(action);
            change.setOperator(operator);
            change.setOperatedAt(now);
            batch.add(change);
            if (batch.size() >= 1000) {
                changeLogMapper.insertBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            changeLogMapper.insertBatch(batch);
        }
    }

    private void insertWords(List<String> words, String operator) {
        Instant now = Instant.now();
        List<SensitiveWord> batch = new ArrayList<>(1000);
        for (String word : words) {
            SensitiveWord e = new SensitiveWord();
            e.setWord(word);
            e.setCategory("default");
            e.setStatus(1);
            e.setCreatedBy(operator);
            e.setUpdatedBy(operator);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            batch.add(e);
            if (batch.size() >= 1000) {
                wordMapper.insertBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            wordMapper.insertBatch(batch);
        }
    }

    private String normalizeWord(String raw) {
        if (raw == null) {
            throw new BizException(4001, "词条不能为空");
        }
        String word = raw.trim();
        String reason = validateWord(word);
        if (reason != null) {
            throw new BizException(4001, reason);
        }
        return word;
    }

    private String validateWord(String word) {
        if (word == null || word.isEmpty()) {
            return "词条不能为空";
        }
        if (word.length() > properties.getValidation().getMaxWordLength()) {
            return "词条超过 " + properties.getValidation().getMaxWordLength() + " 字符";
        }
        for (int i = 0; i < word.length(); i++) {
            if (Character.isISOControl(word.charAt(i))) {
                return "词条包含换行或控制字符";
            }
        }
        return null;
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "default" : category.trim();
    }

    private int normalizeStatus(Integer status) {
        return status == null || status != 0 ? 1 : 0;
    }

    private SensitiveWordDtos.ItemResponse toItem(SensitiveWord e) {
        return new SensitiveWordDtos.ItemResponse(
                e.getId(), e.getWord(), e.getCategory(), e.getStatus(), e.getRemark(),
                e.getCreatedBy(), e.getUpdatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private Map<String, Object> toMap(SensitiveWord e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("word", e.getWord());
        m.put("category", e.getCategory());
        m.put("status", e.getStatus());
        m.put("remark", e.getRemark());
        return m;
    }

    private void recordChange(String word, String action, Object before, Object after) {
        SensitiveWordChangeLog change = new SensitiveWordChangeLog();
        change.setWord(word);
        change.setAction(action);
        change.setBeforeJson(toJson(before));
        change.setAfterJson(toJson(after));
        change.setOperator(operator());
        change.setOperatedAt(Instant.now());
        changeLogMapper.insert(change);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("敏感词变更日志 JSON 序列化失败", e);
            return null;
        }
    }

    private String operator() {
        Long uid = currentUser.userId();
        return uid == null ? "system" : "uid:" + uid;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Parsed(List<SensitiveWordDtos.ImportRow> rows, int total) {
        List<String> validWords() {
            return rows.stream()
                    .filter(r -> "OK".equals(r.status()))
                    .map(SensitiveWordDtos.ImportRow::word)
                    .distinct()
                    .toList();
        }
    }
}
