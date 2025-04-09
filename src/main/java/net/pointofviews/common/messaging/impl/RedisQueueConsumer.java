package net.pointofviews.common.messaging.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.common.messaging.MessageConsumer;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisQueueConsumer implements MessageConsumer {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM_PREFIX = "stream:";
    private static final String GROUP_NAME = "pov-group";
    private static final String CONSUMER_NAME = "pov-consumer";

    @PostConstruct
    public void init() {
        getTopics().forEach(this::initializeStream);
    }

    private void initializeStream(String topic) {
        String streamKey = STREAM_PREFIX + topic;
        try {
            if (!redisTemplate.hasKey(streamKey)) {
                redisTemplate.opsForStream().add(streamKey, Collections.singletonMap("init", "true"));
                log.info("Stream created: {}", streamKey);
            }

            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), GROUP_NAME);
                log.info("Consumer group created for stream: {}", streamKey);
            } catch (Exception e) {
                log.debug("Consumer group already exists for stream: {}", streamKey);
            }
        } catch (Exception e) {
            log.error("Failed to initialize stream {}: {}", streamKey, e.getMessage());
        }
    }

    @Override
    public Object consume(String topic) {
        String streamKey = STREAM_PREFIX + topic;
        try {
            MapRecord<String, Object, Object> record = readPendingMessage(streamKey);
            if (record != null) {
                return processRecord(streamKey, record);
            }

            record = readNewMessage(streamKey);
            if (record != null) {
                return processRecord(streamKey, record);
            }

            return null;
        } catch (Exception e) {
            log.error("Error consuming message from stream {}: {}", streamKey, e.getMessage());
            return null;
        }
    }

    private MapRecord<String, Object, Object> readPendingMessage(String streamKey) {
        try {
            // 미처리 메시지 정보 조회
            PendingMessagesSummary pendingSummary = redisTemplate.opsForStream()
                    .pending(streamKey, GROUP_NAME);

            if (pendingSummary != null && pendingSummary.getTotalPendingMessages() > 0) {
                PendingMessages pendingMessages = redisTemplate.opsForStream()
                        .pending(streamKey, Consumer.from(GROUP_NAME, CONSUMER_NAME),
                                org.springframework.data.domain.Range.unbounded(), 1);

                if (!pendingMessages.isEmpty()) {
                    RecordId messageId = pendingMessages.iterator().next().getId();
                    List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream()
                            .claim(streamKey, GROUP_NAME, CONSUMER_NAME, Duration.ZERO, messageId);

                    if (!claimed.isEmpty()) {
                        return claimed.get(0);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error reading pending messages: {}", e.getMessage());
        }
        return null;
    }

    private MapRecord<String, Object, Object> readNewMessage(String streamKey) {
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(Consumer.from(GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

        return messages != null && !messages.isEmpty() ? messages.get(0) : null;
    }

    private Object processRecord(String streamKey, MapRecord<String, Object, Object> record) {
        redisTemplate.opsForStream().acknowledge(streamKey, GROUP_NAME, record.getId());

        return record.getValue();
    }

    private Set<String> getTopics() {
        return Set.of("notice");
    }
}