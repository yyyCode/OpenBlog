package com.yqz.openblog.site;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.site.entity.SiteConfig;
import com.yqz.openblog.site.repo.SiteConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点配置 Service。
 * key-value 结构，getAllConfigs 返回全量 Map，updateConfigs 批量 upsert。
 */
@Service
public class SiteConfigService {

    private static final Logger log = LoggerFactory.getLogger(SiteConfigService.class);

    private final SiteConfigMapper siteConfigMapper;

    public SiteConfigService(SiteConfigMapper siteConfigMapper) {
        this.siteConfigMapper = siteConfigMapper;
    }

    /**
     * 读取全量配置，以 configKey → configValue 返回，保持插入顺序。
     */
    public Map<String, String> getAllConfigs() {
        List<SiteConfig> rows = siteConfigMapper.selectList(
                Wrappers.lambdaQuery(SiteConfig.class).orderByAsc(SiteConfig::getId));
        Map<String, String> map = new LinkedHashMap<>();
        for (SiteConfig row : rows) {
            map.put(row.getConfigKey(), row.getConfigValue() != null ? row.getConfigValue() : "");
        }
        return map;
    }

    /**
     * 批量 upsert 配置。
     * 只处理传入的 key，不影响其他已有配置。
     */
    @Transactional
    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) return;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) continue;
            try {
                siteConfigMapper.upsert(key.trim(), value != null ? value : "");
            } catch (Exception e) {
                log.warn("upsert site_config failed: key={}", key, e);
            }
        }
    }
}
