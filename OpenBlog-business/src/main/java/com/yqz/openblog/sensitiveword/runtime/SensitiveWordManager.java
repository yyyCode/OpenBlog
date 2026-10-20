package com.yqz.openblog.sensitiveword.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.config.SensitiveWordProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.sensitiveword.entity.SensitiveWord;
import com.yqz.openblog.sensitiveword.entity.SensitiveWordVersion;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordVersionMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 敏感词运行时：启动加载、轮询刷新、原子替换、节点心跳、可选影子对比。
 * <p>
 * 读路径只访问 JVM 内存；Redis/MySQL 只在启动、刷新与管理操作中出现。
 */
@Component
public class SensitiveWordManager {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordManager.class);

    private static final int CONTEXT_PADDING = 12;

    private final RedisOps redisOps;
    private final StringRedisTemplate redisTemplate;
    private final SensitiveWordProperties properties;
    private final SensitiveWordVersionMapper versionMapper;
    private final SensitiveWordMapper wordMapper;
    private final WordMatcherFactory matcherFactory;

    private final AtomicReference<SensitiveWordSnapshot> current =
            new AtomicReference<>(SensitiveWordSnapshot.empty());
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

    private volatile WordMatcher shadowMatcher;
    private volatile boolean ready;
    private volatile String state = "UNAVAILABLE";
    /** 管理台热切换的引擎覆盖值；null 表示跟随 {@code openblog.sensitive-word.engine}。 */
    private volatile SensitiveWordProperties.Engine engineOverride;

    private final LongAdder shadowSamples = new LongAdder();
    private final LongAdder shadowMismatches = new LongAdder();
    private final LongAdder matchSamples = new LongAdder();
    private final LongAdder matchNanos = new LongAdder();
    private final LongAdder shadowNanos = new LongAdder();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sensitive-word-runtime");
        t.setDaemon(true);
        return t;
    });

    private final String instanceId;

    public SensitiveWordManager(RedisOps redisOps,
                                StringRedisTemplate redisTemplate,
                                SensitiveWordProperties properties,
                                SensitiveWordVersionMapper versionMapper,
                                SensitiveWordMapper wordMapper,
                                WordMatcherFactory matcherFactory,
                                @Value("${spring.application.name:openblog-business}") String appName,
                                @Value("${server.port:8082}") String port) {
        this.redisOps = redisOps;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.versionMapper = versionMapper;
        this.wordMapper = wordMapper;
        this.matcherFactory = matcherFactory;
        String configured = properties.getNode().getId();
        this.instanceId = configured == null || configured.isBlank()
                ? appName + "-" + hostname() + "-" + port
                : configured;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        loadAtStartup();
        long pollMs = Math.max(1_000, properties.getRefresh().getPollInterval().toMillis());
        scheduler.scheduleWithFixedDelay(this::pollSafely, pollMs, pollMs, TimeUnit.MILLISECONDS);
        long heartbeatMs = Math.max(1_000, properties.getNode().getHeartbeatInterval().toMillis());
        scheduler.scheduleWithFixedDelay(this::heartbeatSafely, 0, heartbeatMs, TimeUnit.MILLISECONDS);
        log.info("SensitiveWordManager started. instance={}, engine={}, shadow={}, state={}",
                instanceId, properties.getEngine(), properties.getShadowEngine(), state);
    }

    private void loadAtStartup() {
        refreshEngineOverrideFromRedis();
        Long redisVersion = readCurrentVersion();
        SensitiveWordVersion latest = latestPublished();
        boolean redisLoaded = redisVersion != null && redisVersion > 0 && loadFromRedis(redisVersion);
        if (redisLoaded) {
            // Redis 落后于 DB 最新 PUBLISHED（发布在写 current 后崩溃、状态回填失败等）时自愈到 DB 版本
            if (latest == null || latest.getId() <= redisVersion) {
                return;
            }
            log.warn("敏感词 Redis 版本 v{} 落后于 DB 最新已发布 v{}，改从 DB 加载",
                    redisVersion, latest.getId());
        }

        if (latest != null && loadFromDb(latest)) {
            bestEffortWriteRedis(latest);
            return;
        }
        if (redisLoaded) {
            log.warn("敏感词 DB 最新版本加载失败，继续使用 Redis v{}", redisVersion);
            return;
        }

        // 兜底：Redis 与已发布版本都不可用时，直接用工作集构建（首次播种 / Redis 故障场景）
        if (loadWorkingSet()) {
            log.warn("敏感词快照不可用，已回退到 MySQL 工作集构建（version=0）");
            return;
        }

        ready = false;
        state = "UNAVAILABLE";
        log.error("敏感词词库不可用：Redis 与 MySQL 均无可用数据。failMode={}", properties.getFailMode());
    }

    private boolean loadFromRedis(long version) {
        Optional<String> value = redisOps.get(RedisKeys.sensitiveWordSnapshot(version));
        if (value.isEmpty()) {
            log.warn("敏感词快照缺失，version={}", version);
            return false;
        }
        try {
            SnapshotCodec.Decoded decoded = SnapshotCodec.parseRedisValue(value.get());
            String checksum = SnapshotCodec.sha256Hex(decoded.plain());
            if (!checksum.equals(decoded.checksum())) {
                log.error("敏感词快照 checksum 不匹配，version={}", version);
                return false;
            }
            applySnapshot(version, SnapshotCodec.decode(decoded.plain()), checksum);
            return true;
        } catch (Exception e) {
            log.error("敏感词快照解析失败，version={}", version, e);
            return false;
        }
    }

    private boolean loadFromDb(SensitiveWordVersion version) {
        try {
            if (version.getSnapshotBlob() == null || version.getSnapshotBlob().length == 0) {
                return false;
            }
            byte[] plain = SnapshotCodec.gunzip(version.getSnapshotBlob());
            String checksum = SnapshotCodec.sha256Hex(plain);
            if (version.getChecksum() != null && !checksum.equals(version.getChecksum())) {
                log.error("敏感词 DB 快照 checksum 不匹配，version={}", version.getId());
                return false;
            }
            applySnapshot(version.getId(), SnapshotCodec.decode(plain), checksum);
            return true;
        } catch (Exception e) {
            log.error("敏感词 DB 快照加载失败，version={}", version.getId(), e);
            return false;
        }
    }

    private boolean loadWorkingSet() {
        try {
            List<String> words = wordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                            .eq(SensitiveWord::getStatus, 1))
                    .stream().map(SensitiveWord::getWord).toList();
            if (words.isEmpty()) {
                return false;
            }
            byte[] plain = SnapshotCodec.encode(words);
            applySnapshot(0, SnapshotCodec.decode(plain), SnapshotCodec.sha256Hex(plain));
            return true;
        } catch (Exception e) {
            log.error("敏感词工作集加载失败", e);
            return false;
        }
    }

    /**
     * 构建并原子替换快照。与 {@link #applyEngineOverride} 互斥：
     * 避免版本刷新和引擎切换并发替换快照时互相覆盖。
     */
    private synchronized void applySnapshot(long version, List<String> words, String checksum) {
        SensitiveWordProperties.Engine engine = effectiveEngine();
        WordMatcher primary = matcherFactory.create(words, engine);
        WordMatcher shadow = buildShadow(words, engine);
        SensitiveWordSnapshot next = new SensitiveWordSnapshot(
                version, words.size(), checksum, primary.engine(), words, primary);
        current.set(next);
        shadowMatcher = shadow;
        ready = true;
        state = words.isEmpty() ? "EMPTY" : "READY";
        resetMetrics();
        log.info("敏感词快照已生效：version={}, words={}, engine={}, shadow={}",
                version, words.size(), primary.engine(), shadow == null ? "none" : shadow.engine());
    }

    private WordMatcher buildShadow(List<String> words, SensitiveWordProperties.Engine primaryEngine) {
        SensitiveWordProperties.Engine shadowEngine = properties.getShadowEngine();
        if (shadowEngine == null
                || shadowEngine == SensitiveWordProperties.Engine.NONE
                || shadowEngine == primaryEngine) {
            return null;
        }
        try {
            return matcherFactory.create(words, shadowEngine);
        } catch (Exception e) {
            log.error("影子引擎构建失败，engine={}", shadowEngine, e);
            return null;
        }
    }

    private void pollSafely() {
        try {
            refreshEngineOverrideFromRedis();
            Long version = readCurrentVersion();
            if (version != null && version > 0 && version > current.get().version()) {
                if (!rebuilding.compareAndSet(false, true)) {
                    return;
                }
                try {
                    if (loadFromRedis(version)) {
                        // applySnapshot 会按最新覆盖值构建引擎，无需再单独切换
                        log.info("敏感词已刷新到 version={}", version);
                    }
                } finally {
                    rebuilding.set(false);
                }
                return;
            }
            applyEngineOverrideIfNeeded();
        } catch (Exception e) {
            log.warn("敏感词轮询失败，保留当前版本 {}", current.get().version(), e);
        }
    }

    /**
     * 从 Redis 读取覆盖值。读失败时保留上一次值，不因 Redis 抖动把引擎切回默认。
     * 与 {@link #applyEngineOverride} 共用监视器，避免旧值覆盖刚写入的新值。
     */
    private synchronized void refreshEngineOverrideFromRedis() {
        try {
            String raw = redisTemplate.opsForValue().get(RedisKeys.CONTENT_SENSITIVE_WORD_ENGINE_OVERRIDE);
            if (raw == null || raw.isBlank()) {
                engineOverride = null;
            } else {
                SensitiveWordProperties.Engine parsed = EngineSelection.parseOverride(raw);
                if (parsed == null) {
                    log.warn("敏感词引擎覆盖值非法，按未设置处理：{}", raw);
                }
                engineOverride = parsed;
            }
        } catch (Exception e) {
            log.warn("读取敏感词引擎覆盖值失败，保留当前值 {}", EngineSelection.toWire(engineOverride), e);
        }
    }

    /** 轮询发现覆盖值与当前快照引擎不一致时，用当前词表就地重建。 */
    private synchronized void applyEngineOverrideIfNeeded() {
        if (!ready) {
            return;
        }
        SensitiveWordSnapshot snapshot = current.get();
        SensitiveWordProperties.Engine target = effectiveEngine();
        if (target == null || target == SensitiveWordProperties.Engine.NONE
                || target.name().equalsIgnoreCase(snapshot.engine())) {
            return;
        }
        try {
            swapEngine(snapshot, target);
        } catch (Exception e) {
            log.error("敏感词引擎热切换失败，保留当前引擎 {}", snapshot.engine(), e);
        }
    }

    /**
     * 管理台热切换入口：请求已写入 Redis，这里先设置本地覆盖值并立即重建，
     * 其他节点由轮询收敛。构建失败时保留旧快照，覆盖值留待轮询重试。
     */
    public synchronized EngineSwitchOutcome applyEngineOverride(SensitiveWordProperties.Engine override) {
        SensitiveWordProperties.Engine target = override == null ? properties.getEngine() : override;
        if (target == null || target == SensitiveWordProperties.Engine.NONE) {
            throw new IllegalStateException("不支持把主引擎切换为 NONE，请使用 enabled 开关停用过滤");
        }
        SensitiveWordSnapshot snapshot = current.get();
        String previous = snapshot.engine();
        engineOverride = override;
        if (!ready) {
            log.info("敏感词引擎覆盖值已记录（词库未就绪）：{}", EngineSelection.toWire(target));
            return new EngineSwitchOutcome(EngineSelection.toWire(target), previous, 0, 0, false);
        }
        long t0 = System.nanoTime();
        try {
            swapEngine(snapshot, target);
        } catch (RuntimeException e) {
            throw new IllegalStateException("引擎构建失败：" + EngineSelection.toWire(target), e);
        }
        long buildMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("敏感词引擎已热切换：{} -> {}，words={}，build={}ms",
                previous, EngineSelection.toWire(target), snapshot.wordCount(), buildMs);
        return new EngineSwitchOutcome(EngineSelection.toWire(target), previous,
                snapshot.wordCount(), buildMs, true);
    }

    private void swapEngine(SensitiveWordSnapshot snapshot, SensitiveWordProperties.Engine target) {
        WordMatcher matcher = matcherFactory.create(snapshot.words(), target);
        WordMatcher shadow = buildShadow(snapshot.words(), target);
        current.set(new SensitiveWordSnapshot(
                snapshot.version(), snapshot.wordCount(), snapshot.checksum(),
                matcher.engine(), snapshot.words(), matcher));
        shadowMatcher = shadow;
        resetMetrics();
    }

    private SensitiveWordProperties.Engine effectiveEngine() {
        SensitiveWordProperties.Engine override = engineOverride;
        return override == null ? properties.getEngine() : override;
    }

    public String engineOverrideName() {
        return EngineSelection.toWire(engineOverride);
    }

    public String configuredEngineName() {
        return EngineSelection.toWire(properties.getEngine());
    }

    /** 热切换结果；applied=false 表示词库尚未就绪，覆盖值已记录。 */
    public record EngineSwitchOutcome(String engine, String previousEngine,
                                      int wordCount, long buildMs, boolean applied) {
    }

    private void heartbeatSafely() {
        try {
            redisOps.set(RedisKeys.sensitiveWordNode(instanceId),
                    String.valueOf(current.get().version()),
                    properties.getNode().getTtl());
        } catch (Exception e) {
            log.debug("敏感词节点心跳失败", e);
        }
    }

    private Long readCurrentVersion() {
        return redisOps.get(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT)
                .map(v -> {
                    try {
                        return Long.parseLong(v.trim());
                    } catch (NumberFormatException e) {
                        log.warn("敏感词 current 版本号格式非法：{}", v);
                        return null;
                    }
                })
                .orElse(null);
    }

    private SensitiveWordVersion latestPublished() {
        try {
            return versionMapper.selectOne(Wrappers.<SensitiveWordVersion>lambdaQuery()
                    .eq(SensitiveWordVersion::getStatus, "PUBLISHED")
                    .orderByDesc(SensitiveWordVersion::getId)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            // 迁移未执行 / DB 暂时不可用时不能拖垮应用启动，降级到工作集或 fail-mode
            log.warn("读取敏感词已发布版本失败（将尝试工作集兜底）", e);
            return null;
        }
    }

    private void bestEffortWriteRedis(SensitiveWordVersion version) {
        try {
            // snapshot_blob 本身已是 gzip，直接复用，避免重复压缩
            byte[] gz = version.getSnapshotBlob();
            redisOps.set(RedisKeys.sensitiveWordSnapshot(version.getId()),
                    SnapshotCodec.toRedisValue(version.getChecksum(), gz),
                    Duration.ofDays(properties.getSnapshot().getTtlDays()));
            // current 只允许写大：Redis 可能已被更新实例推进到更高版本，回写小版本会让新节点倒退
            Long existing = readCurrentVersion();
            if (existing == null || existing < version.getId()) {
                redisOps.set(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT,
                        String.valueOf(version.getId()), Duration.ofDays(3650));
            }
        } catch (Exception e) {
            log.warn("敏感词快照回写 Redis 失败（不影响运行）", e);
        }
    }

    /** 是否命中；命中时打 WARN（命中词 + 局部上下文），并按需执行影子对比。 */
    public boolean contains(String text) {
        if (!properties.isEnabled() || text == null || text.isBlank()) {
            return false;
        }
        if (!ready) {
            return properties.getFailMode() == SensitiveWordProperties.FailMode.REJECT_ALL;
        }
        SensitiveWordSnapshot snapshot = current.get();
        long t0 = System.nanoTime();
        HitRange first = snapshot.matcher().firstHit(text);
        long t1 = System.nanoTime();
        recordMatch(t1 - t0);
        boolean hit = first != null;
        if (hit) {
            logHit(text, first);
        }
        compareShadow(text, hit, null, t0, t1);
        return hit;
    }

    /** 全量过滤，供在线测试与替换场景使用。 */
    public FilterResult filter(String text) {
        if (!properties.isEnabled() || text == null || text.isEmpty()) {
            return FilterResult.empty(text);
        }
        if (!ready) {
            // 与 contains 的 REJECT_ALL 语义保持一致，否则管理台测试结果会和真实拦截相反
            return properties.getFailMode() == SensitiveWordProperties.FailMode.REJECT_ALL
                    ? new FilterResult(true, text, List.of(), List.of())
                    : FilterResult.empty(text);
        }
        SensitiveWordSnapshot snapshot = current.get();
        long t0 = System.nanoTime();
        FilterResult result = snapshot.matcher().filter(text);
        long t1 = System.nanoTime();
        recordMatch(t1 - t0);
        compareShadow(text, result.hit(), result.filteredText(), t0, t1);
        return result;
    }

    private void compareShadow(String text, boolean primaryHit, String primaryFiltered, long t0, long t1) {
        WordMatcher shadow = shadowMatcher;
        if (shadow == null || properties.getShadowSampleRate() <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= properties.getShadowSampleRate()) {
            return;
        }
        try {
            long s0 = System.nanoTime();
            FilterResult shadowResult = shadow.filter(text);
            long s1 = System.nanoTime();
            shadowSamples.increment();
            shadowNanos.add(s1 - s0);
            boolean mismatch = shadowResult.hit() != primaryHit
                    || (primaryFiltered != null && !primaryFiltered.equals(shadowResult.filteredText()));
            if (mismatch) {
                shadowMismatches.increment();
                if (properties.isShadowLogMismatch()) {
                    log.warn("敏感词影子结果不一致 primaryEngine={} shadowEngine={} textLength={} primaryHit={} shadowHit={}",
                            current.get().engine(), shadow.engine(), text.length(), primaryHit, shadowResult.hit());
                }
            }
        } catch (Exception e) {
            log.warn("敏感词影子对比异常", e);
        }
    }

    private void logHit(String text, HitRange hit) {
        try {
            int end = Math.min(Math.max(hit.endExclusive(), 0), text.length());
            int begin = Math.min(Math.max(hit.start(), 0), end);
            String word = text.substring(begin, end);
            int start = Math.max(0, begin - CONTEXT_PADDING);
            int contextEnd = Math.min(text.length(), end + CONTEXT_PADDING);
            String context = text.substring(start, contextEnd).replaceAll("\\s+", " ");
            log.warn("敏感词命中 word={} context={}", word, context);
        } catch (Exception e) {
            log.warn("敏感词命中日志生成失败", e);
        }
    }

    private void resetMetrics() {
        shadowSamples.reset();
        shadowMismatches.reset();
        shadowNanos.reset();
        matchSamples.reset();
        matchNanos.reset();
    }

    /** 记录每次匹配耗时，供管理台展示平均耗时（LongAdder 开销可忽略）。 */
    private void recordMatch(long nanos) {
        matchSamples.increment();
        matchNanos.add(nanos);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    public long localVersion() {
        return current.get().version();
    }

    public int wordCount() {
        return current.get().wordCount();
    }

    public String engine() {
        return current.get().engine();
    }

    public String state() {
        return state;
    }

    public boolean isReady() {
        return ready;
    }

    public String shadowEngine() {
        WordMatcher shadow = shadowMatcher;
        return shadow == null ? "none" : shadow.engine();
    }

    public long shadowSamples() {
        return shadowSamples.sum();
    }

    public long shadowMismatches() {
        return shadowMismatches.sum();
    }

    /** 主引擎平均匹配耗时（µs），覆盖全部 contains/filter 调用，不依赖影子开关。 */
    public double primaryAvgUs() {
        long samples = matchSamples.sum();
        return samples == 0 ? 0 : matchNanos.sum() / (double) samples / 1000.0;
    }

    public double shadowAvgUs() {
        long samples = shadowSamples.sum();
        return samples == 0 ? 0 : shadowNanos.sum() / (double) samples / 1000.0;
    }
}
