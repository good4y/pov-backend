package net.pointofviews.notice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.common.messaging.MessageConsumer;
import net.pointofviews.common.service.RedisService;
import net.pointofviews.member.domain.MemberFcmToken;
import net.pointofviews.member.repository.MemberFcmTokenRepository;
import net.pointofviews.notice.domain.FcmResult;
import net.pointofviews.notice.domain.Notice;
import net.pointofviews.notice.domain.NoticeReceive;
import net.pointofviews.notice.domain.NoticeSend;
import net.pointofviews.notice.dto.request.CreateNoticeTemplateRequest;
import net.pointofviews.notice.dto.request.SendNoticeRequest;
import net.pointofviews.notice.dto.response.CreateNoticeTemplateResponse;
import net.pointofviews.notice.dto.response.ReadNoticeResponse;
import net.pointofviews.notice.exception.NoticeException;
import net.pointofviews.notice.repository.NoticeReceiveRepository;
import net.pointofviews.notice.repository.NoticeRepository;
import net.pointofviews.notice.repository.NoticeSendRepository;
import net.pointofviews.notice.service.NoticeService;
import net.pointofviews.notice.utils.FcmUtil;
import net.pointofviews.notice.utils.NoticeUtil;
import net.pointofviews.review.repository.ReviewRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 알림 서비스 구현 클래스
 * 알림 템플릿 저장, 알림 전송, 알림 조회 기능을 제공합니다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class NoticeServiceImpl implements NoticeService {
    private final NoticeRepository noticeRepository;
    private final NoticeSendRepository noticeSendRepository;
    private final NoticeReceiveRepository noticeReceiveRepository;
    private final MemberFcmTokenRepository memberFcmTokenRepository;
    private final ReviewRepository reviewRepository;
    private final FcmUtil fcmUtil;
    private final RedisService stringRedisService;
    private final MessageConsumer messageConsumer;
    private static final int FCM_BATCH_SIZE = 500; // FCM 제한
    private final TaskExecutor noticeTaskExecutor;

    /**
     * 알림 템플릿을 저장합니다.
     *
     * @param adminId 관리자 ID
     * @param request 알림 템플릿 생성 요청 객체
     * @return 생성된 알림 템플릿 응답 객체
     */
    @Override
    @Transactional
    public CreateNoticeTemplateResponse saveNoticeTemplate(UUID adminId, CreateNoticeTemplateRequest request) {
        Notice notice = Notice.builder()
                .memberId(adminId)
                .noticeType(request.noticeType())
                .noticeContent(request.content())
                .noticeTitle(request.title())
                .description(request.description())
                .build();

        Notice savedNotice = noticeRepository.save(notice);
        return new CreateNoticeTemplateResponse(
                savedNotice.getId(),
                savedNotice.getNoticeTitle(),
                savedNotice.getNoticeContent(),
                savedNotice.getNoticeType(),
                savedNotice.getDescription(),
                savedNotice.isActive(),
                savedNotice.getCreatedAt()
        );
    }

    /**
     * 알림을 비동기적으로 전송합니다.
     * 메시지 큐에서 알림 메시지를 소비하여 처리합니다.
     *
     * @param request 알림 전송 요청 객체
     */
    @Override
    @Async("noticeTaskExecutor")
    @Transactional
    public void sendNotice(SendNoticeRequest request) {
        Object notice;
        while ((notice = messageConsumer.consume("notice")) != null) {
            processNoticeMessage(notice, request);
        }
        log.info("메시지 큐에 더 이상 알림 메시지가 없습니다");
    }

    /**
     * 알림 메시지를 처리합니다.
     *
     * @param notice  알림 메시지 객체
     * @param request 알림 전송 요청 객체
     */
    @Transactional
    protected void processNoticeMessage(Object notice, SendNoticeRequest request) {
        List<String> genreCodes = List.of();
        String noticeMessage = null;
        Long reviewId = null;

        if (notice instanceof Map) {
            Map<String, Object> noticeMap = (Map<String, Object>) notice;
            genreCodes = (List<String>) noticeMap.get("genreCodes");
            noticeMessage = (String) noticeMap.get("noticeMessage");
            Number reviewIdNumber = (Number) noticeMap.get("reviewId");
            reviewId = reviewIdNumber != null ? reviewIdNumber.longValue() : null;
        }

        Set<UUID> targetMembers = getTargetMembers(genreCodes);

        if (targetMembers.isEmpty()) {
            log.info("모든 장르에 대해 알림을 받을 대상자가 없습니다.");
            return;
        }

        Notice noticeTemplate = noticeRepository.findByIdAndIsActiveTrue(request.noticeTemplateId())
                .orElseThrow(NoticeException.NoticeTemplateNotFoundException::new);

        NoticeSend noticeSend = NoticeSend.builder()
                .notice(noticeTemplate)
                .noticeContentDetail(noticeMessage)
                .isSucceed(true)
                .build();
        noticeSendRepository.save(noticeSend);

        List<UUID> memberList = new ArrayList<>(targetMembers);

        List<MemberFcmToken> allFcmTokens = memberFcmTokenRepository.findActiveTokensByMemberIds(memberList);

        if (allFcmTokens.isEmpty()) {
            log.info("활성화된 FCM 토큰이 없습니다.");
            if (!memberList.isEmpty()) {
                noticeSend.setSucceed(false);
                noticeSendRepository.save(noticeSend);
            }
            return;
        }

        List<String> tokenList = new ArrayList<>(allFcmTokens.size());
        Map<String, MemberFcmToken> tokenMap = new HashMap<>();

        for (MemberFcmToken fcmToken : allFcmTokens) {
            tokenMap.put(fcmToken.getFcmToken(), fcmToken);
            tokenList.add(fcmToken.getFcmToken());
        }

        try {
            String noticeContent = request.templateVariables().getOrDefault("notice_content", noticeMessage);

            List<FcmResult> fcmResults = Collections.synchronizedList(new ArrayList<>());
            int batchCount = (tokenList.size() + FCM_BATCH_SIZE - 1) / FCM_BATCH_SIZE; // 올림 계산
            CountDownLatch latch = new CountDownLatch(batchCount);

            for (int i = 0; i < tokenList.size(); i += FCM_BATCH_SIZE) {
                final int startIndex = i;
                final int endIndex = Math.min(i + FCM_BATCH_SIZE, tokenList.size());

                noticeTaskExecutor.execute(() -> {
                    try {
                        String threadName = Thread.currentThread().getName();
                        log.info("스레드 {}에서 배치 [{}-{}] 처리 중", threadName, startIndex, endIndex);

                        List<String> batchTokens = tokenList.subList(startIndex, endIndex);
                        List<FcmResult> batchResults = fcmUtil.sendMessage(
                                batchTokens,
                                noticeTemplate.getNoticeTitle(),
                                noticeContent,
                                noticeSend
                        );

                        fcmResults.addAll(batchResults);
                    } catch (Exception e) {
                        log.error("배치 [{}-{}] 처리 중 오류 발생", startIndex, endIndex, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("일부 배치가 제한 시간 내에 완료되지 않았습니다");
            }

            List<NoticeReceive> successNoticeReceives = new ArrayList<>();
            int totalSentCount = 0;

            for (FcmResult result : fcmResults) {
                if (result.isSuccess()) {
                    MemberFcmToken fcmToken = tokenMap.get(result.getToken());
                    if (fcmToken != null) {
                        NoticeReceive noticeReceive = NoticeReceive.builder()
                                .member(fcmToken.getMember())
                                .noticeSendId(noticeSend.getId())
                                .noticeContent(noticeMessage)
                                .noticeTitle(noticeTemplate.getNoticeTitle())
                                .noticeType(noticeTemplate.getNoticeType())
                                .reviewId(reviewId)
                                .build();
                        successNoticeReceives.add(noticeReceive);
                        totalSentCount++;
                    }
                } else if (result.isInvalidToken()) {
                    memberFcmTokenRepository.deactivateToken(result.getToken());
                }
            }

            if (!successNoticeReceives.isEmpty()) {
                saveBatchNoticeReceives(successNoticeReceives);
            }

            log.info("알림 전송 완료: 총 {}개 중 {}개 성공", tokenList.size(), totalSentCount);

            if (totalSentCount == 0 && !memberList.isEmpty()) {
                noticeSend.setSucceed(false);
                noticeSendRepository.save(noticeSend);
            }

        } catch (Exception e) {
            log.error("알림 메시지 처리 실패", e);
            noticeSend.setSucceed(false);
            noticeSendRepository.save(noticeSend);
        }
    }

    /**
     * 알림 수신 정보를 배치로 저장합니다.
     *
     * @param noticeReceives 저장할 알림 수신 목록
     */
    private void saveBatchNoticeReceives(List<NoticeReceive> noticeReceives) {
        final int SAVE_BATCH_SIZE = 500;

        for (int i = 0; i < noticeReceives.size(); i += SAVE_BATCH_SIZE) {
            int endIndex = Math.min(i + SAVE_BATCH_SIZE, noticeReceives.size());
            List<NoticeReceive> batch = noticeReceives.subList(i, endIndex);
            noticeReceiveRepository.saveAll(batch);
        }
    }

    /**
     * 회원의 알림 목록을 조회합니다.
     *
     * @param memberId 회원 ID
     * @return 알림 목록 응답 객체
     */
    @Override
    public List<ReadNoticeResponse> findNotices(UUID memberId) {
        return noticeReceiveRepository.findByMemberIdWithReviewAndMovieOrderByCreatedAtDesc(memberId)
                .stream()
                .map(receive -> new ReadNoticeResponse(
                        receive.getId(),
                        receive.getNoticeTitle(),
                        receive.getNoticeContent(),
                        receive.getNoticeType(),
                        receive.isRead(),
                        receive.getCreatedAt(),
                        receive.getReviewId(),
                        receive.getReviewId() != null ? reviewRepository.findById(receive.getReviewId())
                                .map(review -> review.getMovie().getId())
                                .orElse(null) : null
                ))
                .toList();
    }

    /**
     * 알림을 읽음 처리(삭제)합니다.
     *
     * @param memberId 회원 ID
     * @param noticeId 알림 ID
     */
    @Override
    @Transactional
    public void updateNotice(UUID memberId, Long noticeId) {
        NoticeReceive noticeReceive = noticeReceiveRepository.findByIdAndMemberId(noticeId, memberId)
                .orElseThrow(NoticeException.NoticeNotFoundException::new);

        noticeReceiveRepository.delete(noticeReceive);
    }

    /**
     * 알림 메시지를 생성합니다.
     *
     * @param request 알림 전송 요청 객체
     * @return 생성된 알림 메시지
     */
    @Override
    public String getNoticeMessage(SendNoticeRequest request) {
        Notice noticeTemplate = noticeRepository.findByIdAndIsActiveTrue(request.noticeTemplateId())
                .orElseThrow(NoticeException.NoticeTemplateNotFoundException::new);

        return NoticeUtil.replaceTemplateVariables(noticeTemplate.getNoticeContent(), request.templateVariables());
    }

    /**
     * 여러 장르에 대한 알림 대상자 목록을 조회합니다.
     *
     * @param genreKeys 장르 코드 목록
     * @return 알림 대상자 ID 집합
     */
    private Set<UUID> getTargetMembers(List<String> genreKeys) {
        Set<UUID> targetMembers = new HashSet<>();

        for (String genreKey : genreKeys) {
            String key = "genre:preferences:" + genreKey;

            Set<UUID> genreTargetMembers = getTargetMembers(key);

            if (genreTargetMembers.isEmpty()) {
                log.info("장르코드: {}에 대한 알림을 받을 대상자가 없습니다.", genreKey);
            } else {
                log.info("장르코드: {}에 대한 알림 대상자 수: {}", genreKey, genreTargetMembers.size());
            }

            targetMembers.addAll(genreTargetMembers);
        }

        return targetMembers;
    }

    /**
     * Redis에서 특정 장르에 대한 알림 대상자 목록을 조회합니다.
     *
     * @param genreKey 장르 키
     * @return 알림 대상자 ID 집합
     */
    private Set<UUID> getTargetMembers(String genreKey) {
        try {
            Set<String> members = stringRedisService.getSetMembers(genreKey);
            if (members == null) {
                return new HashSet<>();
            }

            return members.stream()
                    .map(member -> {
                        try {
                            return UUID.fromString(member);
                        } catch (IllegalArgumentException e) {
                            log.error("잘못된 UUID 문자열: {}", member);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Redis에서 대상자 목록 조회 실패: {}", e.getMessage(), e);
            throw new NoticeException.RedisOperationFailedException();
        }
    }
}