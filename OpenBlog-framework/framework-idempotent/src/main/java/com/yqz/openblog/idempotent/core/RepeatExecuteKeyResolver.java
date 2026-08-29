package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 @RepeatExecuteLimit#keys 中的 SpEL 表达式为实际字符串值。
 * 复用 AuditLogAspect 的解析模式：DefaultParameterNameDiscoverer + SpEL。
 * 依赖根 pom 的 &lt;parameters&gt;true&lt;/parameters&gt; 取到参数名。
 */
public class RepeatExecuteKeyResolver {

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
            Object value = parser.parseExpression(key).getValue(context);
            result.add(value == null ? "" : String.valueOf(value));
        }
        return result;
    }
}
