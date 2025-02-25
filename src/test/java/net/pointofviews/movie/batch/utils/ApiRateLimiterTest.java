package net.pointofviews.movie.batch.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.catchThrowable;

@ExtendWith(MockitoExtension.class)
class ApiRateLimiterTest {

    private ApiRateLimiter rateLimiter;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        rateLimiter = new ApiRateLimiter();
        executorService = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        rateLimiter.shutdown();
    }

    @Nested
    class Limit {

        @Nested
        class Success {

            @Test
            void 초당_50개_이하로_요청이_제한된다() {
                // given
                int requestCount = 100;
                CountDownLatch latch = new CountDownLatch(requestCount);
                long now = System.currentTimeMillis();

                // when
                while (System.currentTimeMillis() - now < 1000) {
                    executorService.execute(() -> {
                        rateLimiter.limit();
                        latch.countDown();
                    });
                }

                // then
                assertThat(requestCount - latch.getCount()).isEqualTo(50);
            }
        }

        @Nested
        class Failure {

            @Test
            void 인터럽트가_발생하면_RuntimeException을_던진다() {
                // given
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<?> future = executor.submit(() -> {
                    Thread.currentThread().interrupt();
                    rateLimiter.limit();
                });

                // when
                Throwable thrown = catchThrowable(future::get);

                // then
                assertThat(thrown).isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(RuntimeException.class);

                assertThat(thrown.getCause()).hasMessageContaining("Rate limiter interrupted");
            }

        }
    }
}
