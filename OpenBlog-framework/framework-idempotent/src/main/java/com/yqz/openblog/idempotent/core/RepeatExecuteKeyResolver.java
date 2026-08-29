package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 @RepeatExecuteLimit#keys 中的 SpEL 表达式为实际字符串值。
 * 复用 Spring 标准 MethodBasedEvaluationContext + DefaultParameterNameDiscoverer 模式，
 * 依赖根 pom 的 &lt;parameters&gt;true&lt;/parameters&gt; 取到方法参数名。
 *
 * 防御语义：表达式解析失败（如中间对象为 null）只跳过该 Key 并记 warn，
 * 与切面"任一 Key 为空即跳过幂等保护"的策略一致——幂等组件绝不让业务请求 500。
 */
public class RepeatExecuteKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(RepeatExecuteKeyResolver.class);

    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser parser = new SpelExpressionParser();

    public List<String> resolve(ProceedingJoinPoint pjp, String[] keys) {
        List<String> result = new ArrayList<>();
        if (keys == null || keys.length == 0) {
            return result;
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Object[] args = pjp.getArgs();
        EvaluationContext context = new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                Object value = parser.parseExpression(key).getValue(context);
                result.add(value == null ? "" : String.valueOf(value));
            } catch (EvaluationException e) {
                log.warn("[idempotent] SpEL 解析失败，跳过该幂等 Key。expr={}", key, e);
            }
        }
        return result;
    }
}
