package com.yqz.openblog.sensitiveword.runtime;

import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.yqz.openblog.config.SensitiveWordProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 引擎工厂：houbb 内置 allow 表对三个引擎生效一致，避免切换引擎后误伤面变化。 */
class WordMatcherFactoryTest {

    @Test
    void houbbAllowWordsAreSuppressedByAllEngines() {
        List<String> allowWords = WordAllows.defaults().allow();
        assertFalse(allowWords.isEmpty(), "houbb 内置 allow 表为空，测试前提不成立");
        String allowed = allowWords.get(0);

        WordMatcherFactory factory = new WordMatcherFactory(new SensitiveWordProperties());
        List<String> words = List.of(allowed, "测试");

        for (SensitiveWordProperties.Engine engine : List.of(
                SensitiveWordProperties.Engine.HOUBB,
                SensitiveWordProperties.Engine.TRIE,
                SensitiveWordProperties.Engine.AC)) {
            WordMatcher matcher = factory.create(words, engine);
            assertFalse(matcher.contains(allowed), engine + " 未剔除 allow 词：" + allowed);
            assertFalse(matcher.filter(allowed).hit(), engine + " 过滤 allow 词命中：" + allowed);
            assertTrue(matcher.contains("测试"), engine + " 正常词丢失");
        }
    }
}
