package com.yqz.openblog.forum.filter;

import com.yqz.openblog.config.SensitiveWordProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词过滤器回归测试：锁住「内置词库误伤正常表达」这条修复路径。
 * <p>
 * 背景：内置词库把「长期」单独收成一条敏感词（词库里它的邻居是「找长期小姐」「长期出售手枪」
 * 这类垃圾短语，这条裸词疑似切词残留），于是正常语境（「长期共事」）也会被 4003 拦下。
 * 出口是配置白名单 {@code openblog.sensitive-word.allow-words}。
 */
class SensitiveWordFilterTest {

    /** 实际被误拦的一段文字（面试经验分享），正文里除「长期」外没有任何命中。 */
    private static final String INTERVIEW_PARAGRAPH = """
            面试中，技术能力决定你能否进入候选池，而非技术因素往往决定你能否最终拿到 Offer。其中最重要的是语言流畅与表达清晰，回答要有结构、少卡顿、语速适中；其次是普通话标准或口音可懂，不要求播音员水平，但必须让面试官轻松听懂。仪容仪表与精神面貌同样关键，着装得体、整洁清爽、视频面试背景干净光线充足，能体现你对机会的重视。此外，礼貌与职业素养、情绪稳定、自信真诚、时间观念也直接影响印象分——准时到场、不打断对方、遇到难题不慌乱、不会的问题坦诚说，都比硬撑或夸大更加分。

            团队协作与沟通意愿是技术岗常被考察的隐性指标，讲述项目时能体现与产品、测试、运维的配合，面对分歧能平和表达，会让面试官觉得你善于协作。学习意愿与成长潜力、文化匹配度与长期性虽然权重稍低，但对初级岗位和长期共事判断很重要，准备一个“最近在学什么”的具体例子、离职原因聚焦成长方向而非抱怨，都能加分。

            总的来说，技术决定你能不能进面试，非技术因素决定你能不能拿 Offer。其中表达清晰、普通话可懂、仪表得体、情绪稳定、真诚自信，是性价比最高的五个发力点，提前练习远比临场紧张更有用。""";

    @Test
    void interviewParagraph_withAllowWord_notFlagged() {
        assertFalse(filterWith(List.of("长期")).contains(INTERVIEW_PARAGRAPH));
    }

    /**
     * 反证：白名单是这段文字唯一的解药——去掉「长期」后同一段文字立即命中。
     * 本用例依赖内置词库收录了「长期」；若日后升级词库它开始失败，说明上游已自行修掉该误伤，
     * 可连同配置里的白名单条目一起删掉。
     */
    @Test
    void sameParagraphWithoutAllowWord_flaggedByBuiltinDict() {
        assertTrue(filterWith(List.of()).contains(INTERVIEW_PARAGRAPH));
    }

    /** 白名单只豁免列出的词，不影响词库对真正违规内容的拦截。 */
    @Test
    void realDenyWord_stillFlaggedDespiteAllowWords() {
        assertTrue(filterWith(List.of("长期")).contains("本店代开发票，长期有效"));
    }

    /** 豁免是精确匹配：「长期」被加白后，词库里含它的垃圾短语仍然照拦。 */
    @Test
    void spamPhraseBuiltFromAllowWord_stillFlagged() {
        assertTrue(filterWith(List.of("长期")).contains("找长期小姐"));
    }

    /** 空/空白输入不命中（contains 的边界）。 */
    @Test
    void blankText_notFlagged() {
        SensitiveWordFilter filter = filterWith(List.of());
        assertFalse(filter.contains(null));
        assertFalse(filter.contains(""));
        assertFalse(filter.contains("   "));
    }

    private SensitiveWordFilter filterWith(List<String> allowWords) {
        SensitiveWordProperties properties = new SensitiveWordProperties();
        properties.setAllowWords(allowWords);
        return new SensitiveWordFilter(properties);
    }
}
