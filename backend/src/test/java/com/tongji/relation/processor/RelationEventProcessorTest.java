package com.tongji.relation.processor;

import com.tongji.counter.service.UserCounterService;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.service.RelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RelationEventProcessorTest {
    private RelationMapper mapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private UserCounterService counters;
    private RelationService relations;
    private RelationEventProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(RelationMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        counters = mock(UserCounterService.class);
        relations = mock(RelationService.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        processor = new RelationEventProcessor(mapper, redis, counters, relations);
    }

    @Test
    void staleCancelEventRebuildsMirrorFromActiveCanonicalFollowing() {
        when(mapper.existsFollowing(10L, 20L)).thenReturn(1);
        when(mapper.countFollowingActive(10L)).thenReturn(3);
        when(mapper.countFollowerActive(10L)).thenReturn(8);
        when(mapper.countFollowingActive(20L)).thenReturn(4);
        when(mapper.countFollowerActive(20L)).thenReturn(9);

        processor.process(new RelationEvent("FollowCanceled", 10L, 20L, 101L));

        verify(mapper).insertFollower(101L, 20L, 10L, 1);
        verify(mapper, never()).cancelFollower(20L, 10L);
        verify(counters).setRelationCounts(10L, 3, 8);
        verify(counters).setRelationCounts(20L, 4, 9);
        verify(redis).delete(List.of("uf:flws:10", "uf:fans:20"));
    }

    @Test
    void staleCreateEventCancelsMirrorWhenCanonicalFollowingIsInactive() {
        when(mapper.existsFollowing(10L, 20L)).thenReturn(0);
        when(mapper.countFollowingActive(anyLong())).thenReturn(0);
        when(mapper.countFollowerActive(anyLong())).thenReturn(0);

        processor.process(new RelationEvent("FollowCreated", 10L, 20L, 102L));

        verify(mapper).cancelFollower(20L, 10L);
        verify(mapper, never()).insertFollower(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void acquiresUserLocksInStableOrderAndLeavesEventRetryableOnContention() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true, true, false);

        assertThrows(IllegalStateException.class,
                () -> processor.process(new RelationEvent("FollowCreated", 20L, 10L, 103L)));

        verify(values).setIfAbsent(eq("rel:sync:user:10"), anyString(), any(Duration.class));
        verify(values).setIfAbsent(eq("rel:sync:user:20"), anyString(), any(Duration.class));
        verify(mapper, never()).existsFollowing(anyLong(), anyLong());
    }

    @Test
    void skipsAlreadyDeduplicatedEvent() {
        when(redis.hasKey("dedup:rel:FollowCreated:10:20:104")).thenReturn(true);

        processor.process(new RelationEvent("FollowCreated", 10L, 20L, 104L));

        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(mapper, counters);
    }
}
