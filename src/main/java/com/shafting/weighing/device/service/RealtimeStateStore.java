package com.shafting.weighing.device.service;

import com.shafting.weighing.device.dto.RealtimeSnapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
/** 将每次运行的最新实时快照短暂缓存到 Redis。 */
public class RealtimeStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeStateStore.class);
    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RealtimeStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 以 realtime:run:{runId} 保存最新快照，10 秒未更新则自动失效。
     *
     * <p>Redis 临时不可用时只记录警告，SSE 和数据库原始采样继续运行。</p>
     */
    public void save(RealtimeSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set("realtime:run:" + snapshot.runId(),
                    objectMapper.writeValueAsString(snapshot), SNAPSHOT_TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实时状态序列化失败", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis 实时状态写入失败，SSE 推送继续运行: {}", exception.getMessage());
        }
    }
}
