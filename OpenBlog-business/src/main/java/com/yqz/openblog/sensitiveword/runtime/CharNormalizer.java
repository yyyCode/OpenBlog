package com.yqz.openblog.sensitiveword.runtime;

import com.github.houbb.sensitive.word.api.IWordContext;
import com.github.houbb.sensitive.word.api.IWordFormat;
import com.github.houbb.sensitive.word.bs.SensitiveWordContext;
import com.github.houbb.sensitive.word.support.format.WordFormats;

import java.util.List;

/**
 * 三引擎共用的逐字符归一化。
 * <p>
 * 直接复用 houbb 的 {@link IWordFormat} 链（大小写 / 全半角 / 数字样式 / 中文样式 / 英文样式），
 * 保证 Trie / AC 与 houbb 对同一段文本的判定一致。所有规则都是 {@code char -> char} 映射，
 * 不改变长度，因此命中区间可以直接使用原文下标。
 */
public final class CharNormalizer {

    private static final IWordContext CONTEXT = SensitiveWordContext.newInstance();

    // 注意：0.29.5 的 chains(IWordFormat...) 变长参数重载有缺陷（构造了空 List），必须走 List 重载
    private static final CharNormalizer HOUBB_COMPATIBLE = new CharNormalizer(WordFormats.chains(List.of(
            WordFormats.ignoreCase(),
            WordFormats.ignoreWidth(),
            WordFormats.ignoreNumStyle(),
            WordFormats.ignoreChineseStyle(),
            WordFormats.ignoreEnglishStyle())));

    private static final CharNormalizer NONE = new CharNormalizer(WordFormats.none());

    private final IWordFormat format;

    private CharNormalizer(IWordFormat format) {
        this.format = format;
    }

    public static CharNormalizer houbbCompatible() {
        return HOUBB_COMPATIBLE;
    }

    public static CharNormalizer none() {
        return NONE;
    }

    public char normalize(char c) {
        return format.format(c, CONTEXT);
    }

    public String normalize(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = normalize(chars[i]);
        }
        return new String(chars);
    }
}
