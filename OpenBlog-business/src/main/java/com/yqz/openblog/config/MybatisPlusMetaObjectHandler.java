package com.yqz.openblog.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * MyBatis-Plus 自动填充（createdAt/updatedAt）。
 *
 * 后续如果你要把其它实体也接入自动填充，只需要给对应字段加 FieldFill 注解即可。
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        setIfPresent(metaObject, "createdAt", Instant.now());
        setIfPresent(metaObject, "updatedAt", Instant.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setIfPresent(metaObject, "updatedAt", Instant.now());
    }

    private void setIfPresent(MetaObject metaObject, String fieldName, Object value) {
        try {
            Object existing = metaObject.getValue(fieldName);
            if (existing == null) {
                metaObject.setValue(fieldName, value);
            }
        } catch (Exception ignored) {
            // 字段不存在时忽略，避免影响其它实体
        }
    }
}

