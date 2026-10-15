package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析 @RepeatExecuteLimit#keys 中的 SpEL 表达式为实际字符串值。
 * 复用 Spring 标准 MethodBasedEvaluationContext + DefaultParameterNameDiscoverer 模式，
 * 依赖根 pom 的 &lt;parameters&gt;true&lt;/parameters&gt; 取到方法参数名。
 *
 * <p>防御语义（fail-open，且**全有或全无**）：要么整组 Key 全部解析成功并返回，要么整组作废返回
 * {@link Optional#empty()}，由切面跳过幂等保护。两条铁律：
 * <ul>
 *   <li><b>不丢单项</b>：绝不能"只丢掉解析不出来的那一个 Key、其余照用"。那样 Key 粒度会静默变粗
 *       （如 articleId+uid+requestId 退化成 articleId+uid），把同一用户窗口内的正常第二次请求误判成重复——
 *       评论场景下表现为"新内容被吞掉、返回上一条的缓存结果"。</li>
 *   <li><b>不抛异常</b>：语法错误抛 ParseException、求值错误抛 EvaluationException（二者没有共同父类），
 *       一并吞掉并告警，否则异常穿透切面导致业务 500，违反"幂等组件绝不让业务请求失败"的契约。</li>
 * </ul>
 * 求值结果为空（含 SpEL 求值为 null，如客户端未传 requestId）同样视为整组作废。
 */
public class RepeatExecuteKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(RepeatExecuteKeyResolver.class);

    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser parser = new SpelExpressionParser();

    /** 已告警过的位置，避免配置错误在高频请求下反复刷日志（内容上限 = 注解方法数 × keys 数，不会无限增长） */
    private final Set<String> warnedLocations = ConcurrentHashMap.newKeySet();

    /**
     * @return 解析成功的 Key 列表；Key 缺失、表达式空白/非法/求值为空时返回 {@link Optional#empty()}（整组作废）
     */
    public Optional<List<String>> resolve(ProceedingJoinPoint pjp, String[] keys) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String where = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        if (keys == null || keys.length == 0) {
            warnOnce(where + " | <未声明 keys>", "未声明 keys", null);
            return Optional.empty();
        }

        Object[] args = pjp.getArgs();
        EvaluationContext context = new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);
        List<String> values = new ArrayList<>(keys.length);
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                warnOnce(where + " | <空白表达式>", "存在空白表达式", null);
                return Optional.empty();
            }
            Object value;
            try {
                value = parser.parseExpression(key).getValue(context);
            } catch (Exception e) { // ParseException 与 EvaluationException 都归这里，绝不上抛
                warnOnce(where + " | " + key, "表达式无法解析", e);
                return Optional.empty();
            }
            if (value == null || String.valueOf(value).isBlank()) {
                warnOnce(where + " | " + key, "表达式求值为空", null);
                return Optional.empty();
            }
            values.add(String.valueOf(value));
        }
        return Optional.of(values);
    }

    /** 同一位置只告警一次：配置错误不应随请求量刷屏，但也必须看得见 */
    private void warnOnce(String location, String reason, Exception cause) {
        if (!warnedLocations.add(location)) {
            return;
        }
        if (cause == null) {
            log.warn("[idempotent] {}，本次跳过幂等保护（fail-open）。location={}", reason, location);
        } else {
            log.warn("[idempotent] {}，本次跳过幂等保护（fail-open）。location={}", reason, location, cause);
        }
    }
}
