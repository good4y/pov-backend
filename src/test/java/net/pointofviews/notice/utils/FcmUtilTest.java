package net.pointofviews.notice.utils;

import com.google.firebase.messaging.*;
import net.pointofviews.notice.domain.FcmErrorCode;
import net.pointofviews.notice.domain.FcmResult;
import net.pointofviews.notice.domain.NoticeSend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmUtilTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FcmUtil fcmUtil;

    @Test
    void 성공_메세지와_실패_메세지를_응답받는다() throws FirebaseMessagingException {
        // Given
        SendResponse successResponse = mock(SendResponse.class);
        when(successResponse.isSuccessful()).thenReturn(true);

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);

        SendResponse failureResponse = mock(SendResponse.class);
        when(failureResponse.isSuccessful()).thenReturn(false);
        when(failureResponse.getException()).thenReturn(exception);

        BatchResponse batchResponse = mock(BatchResponse.class);
        when(batchResponse.getResponses()).thenReturn(List.of(successResponse, failureResponse));
        when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(batchResponse);

        NoticeSend noticeSend = mock(NoticeSend.class);

        // When
        List<FcmResult> results = fcmUtil.sendMessage(
                List.of("token1", "token2"), "제목", "내용", noticeSend);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).getErrorCode()).isEqualTo(FcmErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void testBatchProcessing() throws FirebaseMessagingException {
        // Given
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            tokens.add("token-" + i);
        }

        List<SendResponse> mockResponses = new ArrayList<>(List.of());
        for (int i = 0; i < 500; i++) {
            SendResponse mockResponse = mock(SendResponse.class);
            when(mockResponse.isSuccessful()).thenReturn(true);
            mockResponses.add(mockResponse);
        }

        BatchResponse batchResponse = mock(BatchResponse.class);
        NoticeSend mockNoticeSend = mock(NoticeSend.class);

        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse);
        when(batchResponse.getResponses()).thenReturn(mockResponses);

        // When
        List<FcmResult> results = fcmUtil.sendMessage(
                tokens,
                "대량 발송 테스트",
                "테스트 내용",
                mockNoticeSend
        );

        // Then
        assertThat(results.size()).isEqualTo(tokens.size());
        assertThat(results.stream().allMatch(FcmResult::isSuccess)).isTrue();
    }
}
