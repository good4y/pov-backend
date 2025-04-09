package net.pointofviews.notice.domain;

import lombok.Getter;

@Getter
public enum FcmErrorCode {
    APNS_AUTH_ERROR("THIRD_PARTY_AUTH_ERROR"),
    INVALID_ARGUMENT("INVALID_ARGUMENT"),
    THIRD_PARTY_AUTH_ERROR("THIRD_PARTY_AUTH_ERROR"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED"),
    UNAVAILABLE("UNAVAILABLE"),
    UNREGISTERED("UNREGISTERED"),
    INTERNAL_ERROR("INTERNAL"),
    UNKNOWN("UNKNOWN");

    private final String code;

    FcmErrorCode(String code) {
        this.code = code;
    }

    public static FcmErrorCode fromCode(String code) {
        for (FcmErrorCode errorCode : FcmErrorCode.values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN;
    }

    public boolean isInvalidToken() {
        return this == UNAVAILABLE || this == UNREGISTERED;
    }
}
