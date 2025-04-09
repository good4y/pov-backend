package net.pointofviews.common.messaging;

import java.util.Map;

public interface MessageProducer {
    void produce(String topic, Map<String, Object> contents);
}