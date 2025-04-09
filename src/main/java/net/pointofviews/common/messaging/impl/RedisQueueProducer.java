package net.pointofviews.common.messaging.impl;

import lombok.RequiredArgsConstructor;
import net.pointofviews.common.messaging.MessageProducer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisQueueProducer implements MessageProducer {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM_PREFIX = "stream:";

    @Override
    public void produce(String topic, Map<String, Object> contents) {
        redisTemplate.opsForStream().add(
                STREAM_PREFIX + topic,
                contents
        );
    }
}
