package net.pointofviews.movie.batch.discover.fixture;

import net.pointofviews.movie.dto.response.SearchMovieDiscoverApiResponse;

import java.util.List;
import java.util.stream.IntStream;

public class SearchMovieDiscoverApiResponseFixture {
    private static final int MAX_MOVIES_PER_PAGE = 20;
    private static final int MAX_CURRENT_PAGE = 500;

    public static SearchMovieDiscoverApiResponse.MovieResult createMovieResult(int id, String title) {
        return new SearchMovieDiscoverApiResponse.MovieResult(
                "backdrop_path_" + id,
                List.of(28, 12),    // CommonCodeId = 01, 02
                id,
                "Original Title " + id,
                "Overview " + id,
                "poster_path_" + id,
                "2024-01-01",
                title
        );
    }

    public static SearchMovieDiscoverApiResponse createResponse(int page, int totalPages, int totalResults, int movieCount) {
        if (movieCount > MAX_MOVIES_PER_PAGE) {
            throw new IllegalArgumentException("movieCount는 최대 " + MAX_MOVIES_PER_PAGE + "까지만 가능합니다.");
        }

        if (page > MAX_CURRENT_PAGE) {
            throw new IllegalArgumentException("page는 최대 " + MAX_CURRENT_PAGE + "까지만 가능합니다.");
        }

        List<SearchMovieDiscoverApiResponse.MovieResult> movies = IntStream.range(0, movieCount)
                .mapToObj(i -> createMovieResult(i + 1, "Movie " + (i + 1)))
                .toList();

        return new SearchMovieDiscoverApiResponse(
                page,
                movies,
                totalPages,
                totalResults
        );
    }

}
