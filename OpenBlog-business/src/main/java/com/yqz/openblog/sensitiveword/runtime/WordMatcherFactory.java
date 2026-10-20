package com.yqz.openblog.sensitiveword.runtime;

import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.yqz.openblog.config.SensitiveWordProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按配置或管理台覆盖值创建匹配引擎；快照构建和热切换都会调用。 */
@Component
public class WordMatcherFactory {

    private final SensitiveWordProperties properties;

    public WordMatcherFactory(SensitiveWordProperties properties) {
        this.properties = properties;
    }

    public WordMatcher create(List<String> words) {
        return create(words, properties.getEngine());
    }

    public WordMatcher create(List<String> words, SensitiveWordProperties.Engine engine) {
        CharNormalizer normalizer = normalizer();
        return switch (engine) {
            case HOUBB -> HoubbWordMatcher.build(words);
            // houbb 构造时挂了内置 allow 表，另外两个引擎需先做同样的剔除，否则少量词会出现引擎间不一致
            case TRIE -> TrieWordMatcher.build(withoutHoubbAllowWords(words, normalizer), normalizer);
            case AC -> AcWordMatcher.build(withoutHoubbAllowWords(words, normalizer), normalizer);
            case NONE -> EmptyWordMatcher.INSTANCE;
        };
    }

    private List<String> withoutHoubbAllowWords(List<String> words, CharNormalizer normalizer) {
        Set<String> allow = new HashSet<>();
        for (String w : WordAllows.defaults().allow()) {
            allow.add(normalizer.normalize(w));
        }
        List<String> filtered = new ArrayList<>(words.size());
        for (String w : words) {
            if (!allow.contains(normalizer.normalize(w))) {
                filtered.add(w);
            }
        }
        return filtered;
    }

    public CharNormalizer normalizer() {
        return properties.getNormalization() == SensitiveWordProperties.Normalization.NONE
                ? CharNormalizer.none()
                : CharNormalizer.houbbCompatible();
    }
}
