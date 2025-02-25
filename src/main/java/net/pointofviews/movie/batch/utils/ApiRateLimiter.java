package net.pointofviews.movie.batch.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class ApiRateLimiter {
    private static final int MAX_REQUESTS_PER_SECOND = 50;
    private final Semaphore semaphore = new Semaphore(MAX_REQUESTS_PER_SECOND);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ApiRateLimiter() {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (semaphore) {
                int currentPermits = semaphore.availablePermits();

                if (currentPermits < MAX_REQUESTS_PER_SECOND) {
                    semaphore.release(MAX_REQUESTS_PER_SECOND - currentPermits);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void limit() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rate limiter interrupted", e);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
