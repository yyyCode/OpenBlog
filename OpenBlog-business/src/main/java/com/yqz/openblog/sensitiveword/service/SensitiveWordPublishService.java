package com.yqz.openblog.sensitiveword.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.audit.annotation.AuditLog;
import com.yqz.openblog.common.BizException;
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
import com.yqz.openblog.sensitiveword.runtime.SnapshotCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 版本发布：生成不可变快照、写 Redis、更新状态。
 * <p>
 * DB 事务只负责版本与审计落库；Redis 写入在事务外执行，失败时把版本置 FAILED，运行节点继续用旧快照。
 */
@Service
public class SensitiveWordPublishService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordPublishService.class);

    private static final int INSERT_BATCH = 1000;

    /** 只删除自己持有的锁，避免锁过期后被第二个发布者持有、却被第一个发布者误删。 */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 操作超过锁 TTL 时续租；token 不匹配说明锁已易主，不再续。 */
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    /** 大词库回滚可能超过锁 TTL，用后台任务续租，避免并发发布。 */
    private static final ScheduledExecutorService LOCK_RENEWER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sensitive-word-lock-renewer");
        t.setDaemon(true);
        return t;
    });

    /** current 指针只允许单调递增，防止启动回写 / 旧版本同步把它写小。 */
    private static final DefaultRedisScript<Long> WRITE_CURRENT_IF_NEWER_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('get', KEYS[1]) "
                    + "if (not v) or (tonumber(v) or 0) < tonumber(ARGV[1]) then "
                    + "redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2]) return 1 end return 0",
            Long.class);

    private final SensitiveWordMapper wordMapper;
    private final SensitiveWordVersionMapper versionMapper;
    private final SensitiveWordChangeLogMapper changeLogMapper;
    private final SensitiveWordStore store;
    private final SensitiveWordProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CurrentUser currentUser;

    public SensitiveWordPublishService(SensitiveWordMapper wordMapper,
                                       SensitiveWordVersionMapper versionMapper,
                                       SensitiveWordChangeLogMapper changeLogMapper,
                                       SensitiveWordStore store,
                                       SensitiveWordProperties properties,
                                       StringRedisTemplate redisTemplate,
                                       PlatformTransactionManager transactionManager,
                                       CurrentUser currentUser) {
        this.wordMapper = wordMapper;
        this.versionMapper = versionMapper;
        this.changeLogMapper = changeLogMapper;
        this.store = store;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.currentUser = currentUser;
    }

    @AuditLog(action = "SENSITIVE_WORD_PUBLISH", entityType = "SensitiveWord")
    public SensitiveWordDtos.PublishResponse publish(String remark, boolean allowEmpty) {
        LockHandle lock = acquireLock();
        try {
            List<String> words = store.enabledWords();
            if (words.isEmpty() && !allowEmpty) {
                throw new BizException(4000, "拒绝发布空词表；如确需发布请勾选允许空词表");
            }
            SensitiveWordVersion previous = store.latestPublished();
            String summary = diffSummary(words, previous);
            Published published = doPublish(words, "PUBLISH", null, remark, summary);
            return new SensitiveWordDtos.PublishResponse(
                    published.version(), published.wordCount(), summary, 10);
        } finally {
            releaseLock(lock);
        }
    }

    @AuditLog(action = "SENSITIVE_WORD_ROLLBACK", entityType = "SensitiveWord", entityId = "#version")
    public SensitiveWordDtos.RollbackResponse rollback(long version, String remark) {
        LockHandle lock = acquireLock();
        try {
            SensitiveWordVersion target = versionMapper.selectById(version);
            if (target == null) {
                throw new BizException(4041, "版本不存在");
            }
            List<String> words = store.wordsOfVersion(target);
            if (words.isEmpty()) {
                throw new BizException(4000, "目标版本词表为空，拒绝回滚");
            }
            String operator = operator();
            String summary = diffSummary(words, store.latestPublished());
            Published published = transactionTemplate.execute(status -> {
                wordMapper.deleteAll();
                insertWords(words, operator, "回滚自 v" + version);
                SensitiveWordVersion v = insertVersion(
                        "ROLLBACK", version, words, summary, remark, operator);
                SensitiveWordChangeLog change = new SensitiveWordChangeLog();
                change.setWord("*");
                change.setAction("ROLLBACK");
                change.setAfterJson("{\"sourceVersion\":" + version + ",\"wordCount\":" + words.size() + "}");
                change.setVersionId(v.getId());
                change.setOperator(operator);
                change.setOperatedAt(Instant.now());
                changeLogMapper.insert(change);
                return new Published(v.getId(), words.size(), v.getSnapshotBlob(), v.getChecksum());
            });
            try {
                distribute(published.version(), published.snapshot(), published.checksum());
            } catch (RuntimeException e) {
                markStatus(published.version(), "FAILED");
                throw e;
            }
            markStatus(published.version(), "PUBLISHED");
            return new SensitiveWordDtos.RollbackResponse(published.version(), version, published.wordCount());
        } finally {
            releaseLock(lock);
        }
    }

    /** 把指定版本重新分发到 Redis（发布失败 / Redis 被清空 / 节点落后）。 */
    @AuditLog(action = "SENSITIVE_WORD_SYNC", entityType = "SensitiveWord", entityId = "#version")
    public void sync(long version) {
        LockHandle lock = acquireLock();
        try {
            SensitiveWordVersion target = versionMapper.selectById(version);
            if (target == null) {
                throw new BizException(4041, "版本不存在");
            }
            if (target.getSnapshotBlob() == null || target.getSnapshotBlob().length == 0) {
                throw new BizException(4000, "版本快照为空，无法同步");
            }
            Long current = readCurrentVersion();
            if (current != null && version < current) {
                throw new BizException(4000,
                        "不允许把当前生效版本回退到 v" + version + "（current=v" + current + "）");
            }
            // 同步失败不能把正在服务的 PUBLISHED 版本改成 FAILED
            distribute(version, target.getSnapshotBlob(), target.getChecksum());
            markStatus(version, "PUBLISHED");
            log.info("敏感词版本已重新分发：version={}, operator={}", version, operator());
        } finally {
            releaseLock(lock);
        }
    }

    private Published doPublish(List<String> words, String kind, Long sourceVersion,
                                String remark, String summary) {
        String operator = operator();
        Published published = transactionTemplate.execute(status -> {
            SensitiveWordVersion v = insertVersion(kind, sourceVersion, words, summary, remark, operator);
            changeLogMapper.assignVersionToUnpublished(v.getId());
            return new Published(v.getId(), words.size(), v.getSnapshotBlob(), v.getChecksum());
        });
        try {
            distribute(published.version(), published.snapshot(), published.checksum());
        } catch (RuntimeException e) {
            markStatus(published.version(), "FAILED");
            throw e;
        }
        markStatus(published.version(), "PUBLISHED");
        log.info("敏感词已发布：version={}, words={}, kind={}, summary={}, operator={}",
                published.version(), published.wordCount(), kind, summary, operator);
        return published;
    }

    private SensitiveWordVersion insertVersion(String kind, Long sourceVersion, List<String> words,
                                               String summary, String remark, String operator) {
        byte[] plain = SnapshotCodec.encode(words);
        SensitiveWordVersion v = new SensitiveWordVersion();
        v.setKind(kind);
        v.setStatus("PENDING");
        v.setSourceVersion(sourceVersion);
        v.setWordCount(words.size());
        v.setChecksum(SnapshotCodec.sha256Hex(plain));
        v.setSnapshotBlob(SnapshotCodec.gzip(plain));
        v.setChangeSummary(summary);
        v.setRemark(remark);
        v.setCreatedBy(operator);
        v.setPublishedAt(Instant.now());
        versionMapper.insert(v);
        return v;
    }

    private void insertWords(List<String> words, String operator, String remark) {
        Instant now = Instant.now();
        List<SensitiveWord> batch = new ArrayList<>(INSERT_BATCH);
        for (String word : words) {
            SensitiveWord e = new SensitiveWord();
            e.setWord(word);
            e.setCategory("default");
            e.setStatus(1);
            e.setRemark(remark);
            e.setCreatedBy(operator);
            e.setUpdatedBy(operator);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            batch.add(e);
            if (batch.size() >= INSERT_BATCH) {
                wordMapper.insertBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            wordMapper.insertBatch(batch);
        }
    }

    private void distribute(long version, byte[] gzipped, String expectedChecksum) {
        byte[] plain = SnapshotCodec.gunzip(gzipped);
        String checksum = SnapshotCodec.sha256Hex(plain);
        if (expectedChecksum != null && !checksum.equals(expectedChecksum)) {
            log.error("敏感词版本快照校验失败，拒绝分发：version={}, expected={}, actual={}",
                    version, expectedChecksum, checksum);
            throw new BizException(5001, "版本快照校验失败，拒绝分发");
        }
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.sensitiveWordSnapshot(version),
                    SnapshotCodec.toRedisValue(checksum, gzipped),
                    Duration.ofDays(properties.getSnapshot().getTtlDays()));
            Long written = redisTemplate.execute(WRITE_CURRENT_IF_NEWER_SCRIPT,
                    List.of(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT),
                    String.valueOf(version),
                    String.valueOf(Duration.ofDays(3650).toSeconds()));
            if (!Long.valueOf(1L).equals(written)) {
                log.warn("敏感词 current 已是更新版本，未回写：version={}", version);
            }
        } catch (Exception e) {
            log.error("敏感词 Redis 分发失败，version={}", version, e);
            throw new BizException(5001, "词库已入库但 Redis 分发失败，请稍后重试或执行同步");
        }
    }

    private Long readCurrentVersion() {
        try {
            String value = redisTemplate.opsForValue().get(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("敏感词 current 版本号格式非法", e);
            return null;
        } catch (Exception e) {
            throw new BizException(5001, "Redis 不可用，无法同步");
        }
    }

    private void markStatus(long version, String status) {
        versionMapper.update(null, Wrappers.<SensitiveWordVersion>lambdaUpdate()
                .eq(SensitiveWordVersion::getId, version)
                .set(SensitiveWordVersion::getStatus, status));
    }

    private String diffSummary(List<String> words, SensitiveWordVersion previous) {
        Set<String> current = new HashSet<>(words);
        Set<String> old = previous == null ? Set.of() : new HashSet<>(store.wordsOfVersion(previous));
        int added = 0;
        for (String w : current) {
            if (!old.contains(w)) {
                added++;
            }
        }
        int removed = 0;
        for (String w : old) {
            if (!current.contains(w)) {
                removed++;
            }
        }
        return "{\"added\":" + added + ",\"removed\":" + removed + ",\"updated\":0}";
    }

    private LockHandle acquireLock() {
        String token = UUID.randomUUID().toString();
        long ttlMillis = Math.max(1_000, properties.getPublish().getLockTtl().toMillis());
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    RedisKeys.CONTENT_SENSITIVE_WORD_PUBLISH_LOCK, token,
                    Duration.ofMillis(ttlMillis));
            if (!Boolean.TRUE.equals(ok)) {
                throw new BizException(4092, "已有发布任务进行中，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(5001, "Redis 不可用，无法发布");
        }
        long interval = Math.max(1_000, ttlMillis / 3);
        ScheduledFuture<?> renewal = LOCK_RENEWER.scheduleWithFixedDelay(
                () -> renewLock(token, ttlMillis), interval, interval, TimeUnit.MILLISECONDS);
        return new LockHandle(token, renewal);
    }

    private void renewLock(String token, long ttlMillis) {
        try {
            redisTemplate.execute(RENEW_LOCK_SCRIPT,
                    List.of(RedisKeys.CONTENT_SENSITIVE_WORD_PUBLISH_LOCK),
                    token, String.valueOf(ttlMillis));
        } catch (Exception e) {
            log.debug("敏感词发布锁续租失败，token={}", token, e);
        }
    }

    private void releaseLock(LockHandle lock) {
        if (lock == null) {
            return;
        }
        lock.renewal().cancel(false);
        try {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                    List.of(RedisKeys.CONTENT_SENSITIVE_WORD_PUBLISH_LOCK), lock.token());
        } catch (Exception e) {
            log.warn("敏感词发布锁释放失败，token={}", lock.token(), e);
        }
    }

    private String operator() {
        Long uid = currentUser.userId();
        return uid == null ? "system" : "uid:" + uid;
    }

    private record Published(long version, int wordCount, byte[] snapshot, String checksum) {
    }

    private record LockHandle(String token, ScheduledFuture<?> renewal) {
    }
}
