package com.yqz.openblog.idempotent.annotation;

import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等防重注解。@Target 预留 ElementType.TYPE（类级）位点，
 * 当前切面仅拦截方法级注解；类级标注暂不生效，请勿在类上使用，待有真实需求再扩展。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatExecuteLimit {

    /** 业务名：拼进锁与标识 Key，区分不同业务（如 comment_create / comment_reply） */
    String name() default "";

    /** 幂等 Key（SpEL 表达式）：定位"哪次请求算重复"，如 {"#articleId", "#uid", "#req.requestId"} */
    String[] keys();

    /** 幂等窗口（秒）。>0 时执行成功后写 Redis 标识并保留 durationTime 秒；=0 只防并发重复执行 */
    long durationTime() default 30L;

    /** 命中防重时的策略 */
    IdempotentStrategy strategy() default IdempotentStrategy.CACHE_REJECT;

    /** 命中防重（且无缓存结果可返回）时的提示语 */
    String message() default "提交频繁，请稍后重试";
}
