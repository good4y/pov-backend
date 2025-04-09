package net.pointofviews.common.messaging;

public interface MessageConsumer {
    Object consume(String topic);
}