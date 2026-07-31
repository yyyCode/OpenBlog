package com.yqz.openblog.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /**
     * 业务动作/操作名，如 PROJECT_UPDATE、USER_DELETE。
     */
    String action();

    /**
     * 实体类型（可选），如 ResearchProject。
     */
    String entityType() default "";

    /**
     * 实体 ID 表达式（SpEL，可选），如 #id 或 #req.id
     */
    String entityId() default "";

    /**
     * 是否记录入参（默认不记录，避免敏感信息与大对象）。
     */
    boolean recordArgs() default false;

    /**
     * 是否记录返回值（默认不记录，避免敏感信息与大对象）。
     */
    boolean recordResult() default false;
}
