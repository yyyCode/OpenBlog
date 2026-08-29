package com.yqz.openblog.comment.service;

import com.yqz.openblog.comment.dto.CommentCreateRequest;
import com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit;
import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommentIdempotencyWiringTest {

    @Test
    void createTopLevel_hasRepeatExecuteLimitWithCommentCreateKey() throws Exception {
        Method m = CommentService.class.getMethod(
                "createTopLevel", Long.class, Long.class, CommentCreateRequest.class);
        RepeatExecuteLimit ann = m.getAnnotation(RepeatExecuteLimit.class);
        assertNotNull(ann, "createTopLevel 应标注 @RepeatExecuteLimit");
        assertEquals("comment_create", ann.name());
        assertArrayEquals(new String[]{"#articleId", "#uid", "#req.requestId"}, ann.keys());
        assertEquals(IdempotentStrategy.RETURN_SAME_RESULT, ann.strategy());
        assertEquals(30L, ann.durationTime());
    }

    @Test
    void reply_hasRepeatExecuteLimitWithCommentReplyKey() throws Exception {
        Method m = CommentService.class.getMethod(
                "reply", Long.class, Long.class, CommentCreateRequest.class);
        RepeatExecuteLimit ann = m.getAnnotation(RepeatExecuteLimit.class);
        assertNotNull(ann, "reply 应标注 @RepeatExecuteLimit");
        assertEquals("comment_reply", ann.name());
        assertArrayEquals(new String[]{"#commentId", "#uid", "#req.requestId"}, ann.keys());
        assertEquals(30L, ann.durationTime());
    }
}
