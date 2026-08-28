package com.yqz.openblog.notification.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

/**
 * notification_outbox Mapper。状态流转用带条件的 UPDATE（乐观：只推进，不倒退）。
 */
@Mapper
public interface NotificationOutboxMapper extends BaseMapper<NotificationOutbox> {

    /** 发布成功：PENDING → PUBLISHED。仅在仍为 PENDING 时推进，防止并发/重复发布竞态。 */
    @Update("UPDATE notification_outbox SET status = 'PUBLISHED' WHERE id = #{id} AND status = 'PENDING'")
    int markPublished(@Param("id") Long id);

    /** 投递成功：→ SENT。仅推进一次（同 messageId 重投时幂等 no-op）。 */
    @Update("UPDATE notification_outbox SET status = 'SENT', sent_at = NOW() WHERE message_id = #{messageId} AND status <> 'SENT'")
    int markSent(@Param("messageId") String messageId);
}
