package net.pointofviews.movie.domain;

import lombok.Getter;

@Getter
public enum KoreanFilmRating {
    ALL("ALL"),
    TWELVE("12"),
    FIFTEEN("15"),
    NINETEEN("19"),
    RESTRICTED("R"),
    NONE("N/A");

    private final String tmdbCode;

    KoreanFilmRating(String tmdbCode) {
        this.tmdbCode = tmdbCode;
    }

    public static KoreanFilmRating of(String tmdbCode) {
        if (tmdbCode == null) {
            return KoreanFilmRating.NONE; // 기본값 처리
        }

        return switch (tmdbCode.toLowerCase()) {
            case "all" -> KoreanFilmRating.ALL;
            case "12" -> KoreanFilmRating.TWELVE;
            case "15" -> KoreanFilmRating.FIFTEEN;
            case "18", "19" -> KoreanFilmRating.NINETEEN;
            default -> KoreanFilmRating.NONE;
        };
    }

}