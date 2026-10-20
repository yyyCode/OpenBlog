package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤配置。
 * <p>
 * 运行时词库来自 MySQL + Redis 版本快照；本类只描述行为开关与引擎选择。
 */
@ConfigurationProperties(prefix = "openblog.sensitive-word")
public class SensitiveWordProperties {

    public enum Engine { HOUBB, TRIE, AC, NONE }

    public enum Normalization { HOUBB_COMPATIBLE, NONE }

    public enum Strategy { REJECT, REPLACE }

    public enum FailMode { STALE, REJECT_ALL, ALLOW_ALL }

    /** 总开关；false 时所有校验直接放行（应急用）。 */
    private boolean enabled = true;

    /** 命中后的业务策略。当前 UGC 入口只使用 REJECT。 */
    private Strategy strategy = Strategy.REJECT;

    /** 快照不可用时的行为。 */
    private FailMode failMode = FailMode.STALE;

    /** 是否过滤文章/项目/公司等受信内容（一期默认关闭）。 */
    private boolean filterTrustedContent = false;

    /** 仅首次部署使用：从 classpath 内置词库播种。 */
    private boolean seedFromClasspath = false;

    /** 匹配引擎：houbb（生产基线）/ trie / ac。 */
    private Engine engine = Engine.HOUBB;

    /** 归一化方式；none 仅用于基准测试。 */
    private Normalization normalization = Normalization.HOUBB_COMPATIBLE;

    /** 影子对比引擎；NONE 表示关闭。 */
    private Engine shadowEngine = Engine.NONE;

    /** 影子采样率，0 关闭。 */
    private double shadowSampleRate = 0.1;

    /** 影子结果不一致时是否打 WARN。 */
    private boolean shadowLogMismatch = true;

    private final Refresh refresh = new Refresh();
    private final Snapshot snapshot = new Snapshot();
    private final Node node = new Node();
    private final Publish publish = new Publish();
    private final Import importConfig = new Import();
    private final Validation validation = new Validation();

    /**
     * 迁移期白名单：播种时把列出的词置为 status=0。
     * 播种完成后可删除本配置项。
     */
    private List<String> allowWords = new ArrayList<>();

    public static class Refresh {
        private Duration pollInterval = Duration.ofSeconds(5);

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }
    }

    public static class Snapshot {
        private int ttlDays = 30;

        public int getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(int ttlDays) {
            this.ttlDays = ttlDays;
        }
    }

    public static class Node {
        private String id = "";
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        private Duration ttl = Duration.ofSeconds(30);

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class Publish {
        private Duration lockTtl = Duration.ofSeconds(30);

        public Duration getLockTtl() {
            return lockTtl;
        }

        public void setLockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
        }
    }

    public static class Import {
        private DataSize maxFileSize = DataSize.ofMegabytes(5);
        private int maxRows = 50_000;

        public DataSize getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(DataSize maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }
    }

    public static class Validation {
        private int maxWordLength = 128;

        public int getMaxWordLength() {
            return maxWordLength;
        }

        public void setMaxWordLength(int maxWordLength) {
            this.maxWordLength = maxWordLength;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public FailMode getFailMode() {
        return failMode;
    }

    public void setFailMode(FailMode failMode) {
        this.failMode = failMode;
    }

    public boolean isFilterTrustedContent() {
        return filterTrustedContent;
    }

    public void setFilterTrustedContent(boolean filterTrustedContent) {
        this.filterTrustedContent = filterTrustedContent;
    }

    public boolean isSeedFromClasspath() {
        return seedFromClasspath;
    }

    public void setSeedFromClasspath(boolean seedFromClasspath) {
        this.seedFromClasspath = seedFromClasspath;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public Normalization getNormalization() {
        return normalization;
    }

    public void setNormalization(Normalization normalization) {
        this.normalization = normalization;
    }

    public Engine getShadowEngine() {
        return shadowEngine;
    }

    public void setShadowEngine(Engine shadowEngine) {
        this.shadowEngine = shadowEngine;
    }

    public double getShadowSampleRate() {
        return shadowSampleRate;
    }

    public void setShadowSampleRate(double shadowSampleRate) {
        this.shadowSampleRate = shadowSampleRate;
    }

    public boolean isShadowLogMismatch() {
        return shadowLogMismatch;
    }

    public void setShadowLogMismatch(boolean shadowLogMismatch) {
        this.shadowLogMismatch = shadowLogMismatch;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public Node getNode() {
        return node;
    }

    public Publish getPublish() {
        return publish;
    }

    public Import getImportConfig() {
        return importConfig;
    }

    /** 与 YAML 的 {@code openblog.sensitive-word.import} 对齐；字段名不能用 Java 关键字 import。 */
    public Import getImport() {
        return importConfig;
    }

    public Validation getValidation() {
        return validation;
    }

    public List<String> getAllowWords() {
        return allowWords;
    }

    public void setAllowWords(List<String> allowWords) {
        this.allowWords = allowWords;
    }
}
