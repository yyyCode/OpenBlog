package com.yqz.openblog.sensitiveword.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.config.SensitiveWordProperties;
import com.yqz.openblog.sensitiveword.entity.SensitiveWord;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 首次部署播种：把 houbb 内置词库复制进工作集并发布 v1。
 * <p>
 * 只在 {@code seed-from-classpath=true} 且工作集为空时执行；执行完应把开关改回 false。
 * 在 ApplicationReadyEvent 之前运行，随后 SensitiveWordManager 会加载刚生成的版本。
 */
@Component
public class SensitiveWordSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordSeedRunner.class);

    private static final int BATCH = 1000;

    private final SensitiveWordProperties properties;
    private final SensitiveWordMapper wordMapper;
    private final SensitiveWordPublishService publishService;

    public SensitiveWordSeedRunner(SensitiveWordProperties properties,
                                   SensitiveWordMapper wordMapper,
                                   SensitiveWordPublishService publishService) {
        this.properties = properties;
        this.wordMapper = wordMapper;
        this.publishService = publishService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedFromClasspath()) {
            return;
        }
        long existing = wordMapper.selectCount(null);
        if (existing > 0) {
            log.info("敏感词工作集已有 {} 条，跳过播种", existing);
            return;
        }

        ClassPathResource resource = new ClassPathResource("sensitive_word_dict.txt");
        if (!resource.exists()) {
            log.error("未找到 classpath:sensitive_word_dict.txt，跳过播种");
            return;
        }

        int inserted = 0;
        List<SensitiveWord> batch = new ArrayList<>(BATCH);
        Instant now = Instant.now();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty() || word.length() > properties.getValidation().getMaxWordLength()) {
                    continue;
                }
                SensitiveWord e = new SensitiveWord();
                e.setWord(word);
                e.setCategory("default");
                e.setStatus(1);
                e.setCreatedBy("system");
                e.setUpdatedBy("system");
                e.setCreatedAt(now);
                e.setUpdatedAt(now);
                batch.add(e);
                if (batch.size() >= BATCH) {
                    wordMapper.insertBatch(batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                wordMapper.insertBatch(batch);
                inserted += batch.size();
            }
        } catch (Exception e) {
            log.error("敏感词播种失败", e);
            return;
        }

        // 迁移期白名单：停用误伤词（原 allow-words 语义）
        for (String allow : properties.getAllowWords()) {
            if (allow == null || allow.isBlank()) {
                continue;
            }
            int updated = wordMapper.update(null, Wrappers.<SensitiveWord>lambdaUpdate()
                    .eq(SensitiveWord::getWord, allow.trim())
                    .set(SensitiveWord::getStatus, 0)
                    .set(SensitiveWord::getUpdatedBy, "system")
                    .set(SensitiveWord::getUpdatedAt, Instant.now()));
            if (updated == 0) {
                SensitiveWord e = new SensitiveWord();
                e.setWord(allow.trim());
                e.setCategory("default");
                e.setStatus(0);
                e.setRemark("迁移自 allow-words");
                e.setCreatedBy("system");
                e.setUpdatedBy("system");
                e.setCreatedAt(Instant.now());
                e.setUpdatedAt(Instant.now());
                wordMapper.insert(e);
            }
        }

        log.info("敏感词播种完成：words={}", inserted);
        try {
            publishService.publish("首次播种", false);
        } catch (Exception e) {
            // Redis 不可用时发布失败；工作集已入库，Manager 会回退到工作集构建，不能阻塞应用启动
            log.error("敏感词首次发布失败（工作集已入库）：{}", e.getMessage());
        }
    }
}
