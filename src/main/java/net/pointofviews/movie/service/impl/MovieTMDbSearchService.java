package net.pointofviews.movie.service.impl;

import net.pointofviews.common.domain.CodeGroupEnum;
import net.pointofviews.common.service.impl.CommonCodeServiceImpl;
import net.pointofviews.common.utils.LocaleUtils;
import net.pointofviews.movie.dto.response.*;
import net.pointofviews.movie.exception.MovieException;
import net.pointofviews.movie.service.MovieApiSearchService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class MovieTMDbSearchService implements MovieApiSearchService {

    @Value("${TMDb.access}")
    private String TMDbApiKey;

    private final RestClient restClient;
    private final CommonCodeServiceImpl commonCodeService;

    public MovieTMDbSearchService(RestClient.Builder restClient, CommonCodeServiceImpl commonCodeService) {
        this.restClient = restClient.baseUrl("https://api.themoviedb.org/3")
                .build();
        this.commonCodeService = commonCodeService;
    }

    @Override
    public SearchMovieApiListResponse searchMovie(String query, int page) {
        SearchMovieApiListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/movie")
                        .queryParam("query", query)
                        .queryParam("page", page)
                        .queryParam("language", LocaleUtils.KOREAN_LANGUAGE_CODE)
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchMovieApiListResponse.class);

        List<SearchMovieApiResponse> results = response.results();

        transformMovieGenreResponse(results);
        return response;
    }

    @Override
    public SearchFilteredMovieDetailResponse searchDetailsMovie(String movieId) {
        SearchMovieDetailApiResponse movieDetails = searchApiDetailsMovie(movieId);
        SearchCreditApiResponse movieCredits = searchLimit5Credit(movieId);
        SearchReleaseApiResponse movieReleases = searchReleaseDate(movieId);

        SearchReleaseApiResponse.Result.ReleaseDate releaseDate = movieReleases.results().stream()
                .findFirst()
                .flatMap(result -> result.release_dates().stream().findFirst())
                .orElse(null);

        List<String> genres = movieDetails.genres().stream()
                .map(SearchMovieDetailApiResponse.TMDbGenreResponse::name)
                .toList();

        String released = null;
        String filmRating = null;

        if (releaseDate != null) {
            released = releaseDate.release_date();
            filmRating = releaseDate.certification();
        }

        return SearchFilteredMovieDetailResponse.of(movieDetails, released, filmRating, genres, movieCredits);
    }

    private SearchMovieDetailApiResponse searchApiDetailsMovie(String movieId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/")
                        .path(movieId)
                        .queryParam("language", LocaleUtils.KOREAN_LANGUAGE_CODE)
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchMovieDetailApiResponse.class);
    }

    @Override
    public SearchCreditApiResponse searchLimit5Credit(String movieId) {
        SearchCreditApiResponse response = searchApiCredit(movieId);
        List<SearchCreditApiResponse.CastResponse> limitCastList = response.cast()
                .subList(0, Math.min(response.cast().size(), 5));
        List<SearchCreditApiResponse.CrewResponse> directors = response.crew().stream()
                .filter(crew -> "director".equalsIgnoreCase(crew.job()))
                .toList();

        return new SearchCreditApiResponse(limitCastList, directors);
    }

    @Override
    public SearchReleaseApiResponse searchReleaseDate(String movieId) {
        SearchReleaseApiResponse response = searchApiReleaseDate(movieId);

        // 한국 개봉 정보 유무 확인
        SearchReleaseApiResponse.Result koreanResult = response.results().parallelStream()
                .filter(result -> "kr".equalsIgnoreCase(result.iso_3166_1()))
                .findFirst()
                .orElse(null);

        if (koreanResult == null) {
            return new SearchReleaseApiResponse(response.id(), List.of());
        }

        // 한국 개봉 정보에서 영화관 개봉 여부
        List<SearchReleaseApiResponse.Result.ReleaseDate> filteredReleaseDateResults = koreanResult.release_dates()
                .parallelStream()
                .filter(releaseDate -> releaseDate.type() == 3)
                .toList();

        if (filteredReleaseDateResults.isEmpty()) {
            return new SearchReleaseApiResponse(response.id(), List.of());
        }

        SearchReleaseApiResponse.Result.ReleaseDate filteredBestResult = filteredReleaseDateResults
                .parallelStream()
                .filter(result -> !result.certification().isEmpty())
                .findFirst()
                .orElse(filteredReleaseDateResults.get(0));

        SearchReleaseApiResponse.Result filtered = new SearchReleaseApiResponse.Result(koreanResult.iso_3166_1(), List.of(filteredBestResult));

        return new SearchReleaseApiResponse(response.id(), List.of(filtered));
    }

    @Override
    public SearchMovieDiscoverApiResponse searchDiscoverMovie(LocalDate start, LocalDate end, int page) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/discover")
                        .path("/movie")
                        .queryParam("release_date.gte", start)
                        .queryParam("release_date.lte", end)
                        .queryParam("region", "KR")
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("with_release_type", 3)
                        .queryParam("page", page)
                        .queryParam("language", LocaleUtils.KOREAN_LANGUAGE_CODE)
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchMovieDiscoverApiResponse.class);
    }

    @Override
    public SearchMovieImageApiResponse searchImageMovie(String movieId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/")
                        .path(movieId)
                        .path("/images")
                        .queryParam("include_image_language", "ko,null")
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchMovieImageApiResponse.class);
    }

    @Override
    public SearchMovieTrendingApiResponse searchTrendingMovie(String timeWindow, int page) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/trending")
                        .path("/movie")
                        .path("/" + timeWindow)
                        .queryParam("language", LocaleUtils.KOREAN_LANGUAGE_CODE)
                        .queryParam("page", page)
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchMovieTrendingApiResponse.class);
    }

    private SearchReleaseApiResponse searchApiReleaseDate(String movieId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/")
                        .path(movieId)
                        .path("/release_dates")
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchReleaseApiResponse.class);
    }

    private SearchCreditApiResponse searchApiCredit(String movieId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/")
                        .path(movieId)
                        .path("/credits")
                        .queryParam("language", LocaleUtils.KOREAN_LANGUAGE_CODE)
                        .build())
                .header("Authorization", "Bearer " + TMDbApiKey)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        this::handleClientError)
                .body(SearchCreditApiResponse.class);
    }

    private void handleClientError(HttpRequest request, ClientHttpResponse response) {
        try {
            String messages = new String(response.getBody().readAllBytes());

            String startKeyword = "\"status_message\":\"";
            String endKeyword = "\"";
            String message = StringUtils.substringBetween(messages, startKeyword, endKeyword);

            throw MovieException.tmdbBadRequest(message);
        } catch (IOException e) {
            throw new RuntimeException("외부 응답 읽기 실패", e);
        }
    }

    private void transformMovieGenreResponse(List<SearchMovieApiResponse> results) {
        for (SearchMovieApiResponse result : results) {
            List<String> genreId = result.genre_ids();

            List<String> stringGenre = new ArrayList<>();
            for (String s : genreId) {
                stringGenre.add(commonCodeService.convertCommonCodeNameToName(s, CodeGroupEnum.MOVIE_GENRE));
            }

            genreId.clear();
            genreId.addAll(stringGenre);
        }
    }
}
