package com.yqz.openblog.sensitiveword.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.sensitiveword.entity.SensitiveWord;
import com.yqz.openblog.sensitiveword.entity.SensitiveWordVersion;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordMapper;
import com.yqz.openblog.sensitiveword.repo.SensitiveWordVersionMapper;
import com.yqz.openblog.sensitiveword.runtime.SnapshotCodec;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 工作集与版本快照的共享读取逻辑。 */
@Component
public class SensitiveWordStore {

    private final SensitiveWordMapper wordMapper;
    private final SensitiveWordVersionMapper versionMapper;

    public SensitiveWordStore(SensitiveWordMapper wordMapper, SensitiveWordVersionMapper versionMapper) {
        this.wordMapper = wordMapper;
        this.versionMapper = versionMapper;
    }

    /** 当前启用词表（字典序）。 */
    public List<String> enabledWords() {
        return wordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                        .eq(SensitiveWord::getStatus, 1)
                        .orderByAsc(SensitiveWord::getWord))
                .stream()
                .map(SensitiveWord::getWord)
                .toList();
    }

    public SensitiveWordVersion latestPublished() {
        return versionMapper.selectOne(Wrappers.<SensitiveWordVersion>lambdaQuery()
                .eq(SensitiveWordVersion::getStatus, "PUBLISHED")
                .orderByDesc(SensitiveWordVersion::getId)
                .last("LIMIT 1"));
    }

    public List<String> wordsOfVersion(SensitiveWordVersion version) {
        if (version == null || version.getSnapshotBlob() == null || version.getSnapshotBlob().length == 0) {
            return List.of();
        }
        return SnapshotCodec.decode(SnapshotCodec.gunzip(version.getSnapshotBlob()));
    }

    /** 最近一次发布后有过编辑（含新增）的词数。 */
    public long editedSince(Instant since) {
        if (since == null) {
            return 0;
        }
        return wordMapper.selectCount(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getStatus, 1)
                .gt(SensitiveWord::getUpdatedAt, since));
    }
}
