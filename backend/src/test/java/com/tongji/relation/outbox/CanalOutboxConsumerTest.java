package com.tongji.relation.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.processor.RelationEventProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class CanalOutboxConsumerTest {
    private static final String MESSAGE = """
            {"table":"outbox","type":"INSERT","data":[{"payload":"{\\"type\\":\\"FollowCreated\\",\\"fromUserId\\":1,\\"toUserId\\":2,\\"id\\":3}"}]}
            """;

    @Test
    void acknowledgesMessageOnlyAfterProcessingSucceeds() {
        RelationEventProcessor processor = mock(RelationEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CanalOutboxConsumer consumer = new CanalOutboxConsumer(new ObjectMapper(), processor);

        consumer.onMessage(MESSAGE, acknowledgment);

        verify(processor).process(new RelationEvent("FollowCreated", 1L, 2L, 3L));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void leavesFailedMessageUnacknowledgedAndPropagatesFailure() {
        RelationEventProcessor processor = mock(RelationEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        doThrow(new IllegalStateException("temporary failure"))
                .when(processor).process(org.mockito.ArgumentMatchers.any(RelationEvent.class));
        CanalOutboxConsumer consumer = new CanalOutboxConsumer(new ObjectMapper(), processor);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(MESSAGE, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }
}
