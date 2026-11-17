package com.shafting.weighing.device.service;

import com.shafting.weighing.device.dto.RealtimeSnapshot;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeStateStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldCacheLatestSnapshotForTenSeconds() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RealtimeStateStore store = new RealtimeStateStore(redisTemplate,
                JsonMapper.builder().addModule(new JavaTimeModule()).build());
        RealtimeSnapshot snapshot = new RealtimeSnapshot(7L, 3L, "TAIL_SHAFT", "RUNNING", "LIFTING",
                new BigDecimal("0.20"), new BigDecimal("7.50"), 600,
                new BigDecimal("90.0"), Instant.EPOCH);

        store.save(snapshot);

        verify(valueOperations).set(eq("realtime:run:7"), contains("\"state\":\"RUNNING\""),
                eq(Duration.ofSeconds(10)));
    }
}
