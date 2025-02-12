package net.pointofviews.movie.batch.discover;

import net.pointofviews.movie.batch.discover.fixture.SearchMovieDiscoverApiResponseFixture;
import net.pointofviews.movie.batch.utils.ApiRateLimiter;
import net.pointofviews.movie.dto.response.SearchMovieDiscoverApiResponse;
import net.pointofviews.movie.service.impl.MovieTMDbSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TMDbMovieDiscoverReaderTest {

    @Mock
    private MovieTMDbSearchService movieService;

    @Mock
    private ApiRateLimiter batchRateLimiter;

    private TMDbMovieDiscoverReader reader;

    @BeforeEach
    void setUp() {
        reader = new TMDbMovieDiscoverReader(movieService, batchRateLimiter, "2024-01-01", "2024-01-10");
    }

    @Nested
    class Success {

        @Test
        void 영화_데이터가_있을_때_첫_페이지_조회_성공() {
            // given
            SearchMovieDiscoverApiResponse response = SearchMovieDiscoverApiResponseFixture.createResponse(1, 3, 30, 1);

            given(movieService.searchDiscoverMovie(any(LocalDate.class), any(LocalDate.class), eq(1)))
                    .willReturn(response);

            // when
            List<SearchMovieDiscoverApiResponse.MovieResult> result = reader.read();

            // then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("Movie 1");
        }

        @Test
        void 더_이상_조회할_영화_데이터가_없으면_null_반환() {
            // given
            SearchMovieDiscoverApiResponse mockResponse = SearchMovieDiscoverApiResponseFixture.createResponse(1, 1, 0, 0);

            given(movieService.searchDiscoverMovie(any(LocalDate.class), any(LocalDate.class), eq(1)))
                    .willReturn(mockResponse);

            // when
            List<SearchMovieDiscoverApiResponse.MovieResult> result = reader.read();

            // then
            assertThat(result).isNull();
        }
    }
}
