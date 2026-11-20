package com.tongji.counter.service.impl;

import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.relation.mapper.RelationMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UserCounterServiceRedisTest {
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connectToLocalRedis() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", 16379);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate();
        redis.setConnectionFactory(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void closeConnection() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void relationReconciliationChangesOnlyTheFirstTwoSdsFields() {
        long userId = 8_000_000_000L + Math.abs(System.nanoTime() % 1_000_000_000L);
        String key = "ucnt:" + userId;
        byte[] original = {
                0, 0, 0, 11, 0, 0, 0, 13,
                0, 0, 0, 17, 0, 0, 0, 19, 0, 0, 0, 23
        };
        try {
            redis.execute((RedisCallback<Void>) connection -> {
                connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), original);
                return null;
            });

            UserCounterServiceImpl service = new UserCounterServiceImpl(
                    redis, mock(KnowPostMapper.class), mock(CounterService.class), mock(RelationMapper.class));
            service.setRelationCounts(userId, 4, 7);

            byte[] updated = redis.execute((RedisCallback<byte[]>) connection ->
                    connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
            assertEquals(4, read32(updated, 0));
            assertEquals(7, read32(updated, 4));
            assertArrayEquals(new byte[]{0, 0, 0, 17, 0, 0, 0, 19, 0, 0, 0, 23},
                    java.util.Arrays.copyOfRange(updated, 8, 20));
        } finally {
            redis.delete(key);
        }
    }

    private static long read32(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xffL) << 24)
                | ((bytes[offset + 1] & 0xffL) << 16)
                | ((bytes[offset + 2] & 0xffL) << 8)
                | (bytes[offset + 3] & 0xffL);
    }
}
