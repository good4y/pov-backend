package net.pointofviews.notice.utils;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.notice.domain.FcmErrorCode;
import net.pointofviews.notice.domain.FcmResult;
import net.pointofviews.notice.domain.NoticeSend;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmUtil {
    private final FirebaseMessaging firebaseMessaging;
    private static final int MAX_RETRY_COUNT = 2;
    private static final long RETRY_DELAY_MS = 1000;

    public List<FcmResult> sendMessage(List<String> batchTokens, String title, String body, NoticeSend noticeSend) {
        List<FcmResult> results = new ArrayList<>();
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .setWebpushConfig(
                                WebpushConfig.builder()
                                        .setNotification(
                                                WebpushNotification.builder()
                                                        .setTitle(title)
                                                        .setBody(body)
                                                        .build()
                                        )
                                        .build()
                        )
                        .addAllTokens(batchTokens)
                        .build();

                BatchResponse response = firebaseMessaging.sendEachForMulticast(message);

                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse sendResponse = responses.get(i);
                    String token = batchTokens.get(i);

                    if (sendResponse.isSuccessful()) {
                        results.add(FcmResult.builder()
                                .token(token)
                                .isSuccess(true)
                                .noticeSend(noticeSend)
                                .build());
                    } else {
                        FirebaseMessagingException fcmException = sendResponse.getException();
                        FcmErrorCode errorCode = FcmErrorCode.fromCode(fcmException.getMessagingErrorCode().toString());

                        results.add(FcmResult.builder()
                                .token(token)
                                .isSuccess(false)
                                .errorCode(errorCode)
                                .noticeSend(noticeSend)
                                .build());
                    }
                }

                return results;

            } catch (FirebaseMessagingException e) {
                lastException = e;
                retryCount++;

                if (retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Failed to send multicast message after {} retries: {}",
                MAX_RETRY_COUNT, lastException.getMessage());

        return batchTokens.stream()
                .map(token -> FcmResult.builder()
                        .token(token)
                        .isSuccess(false)
                        .errorCode(FcmErrorCode.UNKNOWN)
                        .noticeSend(noticeSend)
                        .build())
                .collect(Collectors.toList());
    }
}