package net.pointofviews.movie.batch.discover;

import net.pointofviews.common.domain.CodeGroupEnum;
import net.pointofviews.common.service.CommonCodeService;
import net.pointofviews.movie.batch.discover.fixture.SearchMovieDiscoverApiResponseFixture;
import net.pointofviews.movie.domain.MovieGenre;
import net.pointofviews.movie.dto.response.BatchDiscoverMovieResponse;
import net.pointofviews.movie.dto.response.SearchMovieDiscoverApiResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TMDbMovieDiscoverProcessorTest {

    @InjectMocks
    private TMDbMovieDiscoverProcessor processor;

    @Mock
    private CommonCodeService commonCodeService;

    @Nested
    class Process {

        @Nested
        class Success {

            @Test
            void 영화_데이터를_저장할_수_있게_가공한다() {
                // given
                String image_path = "https://image.tmdb.org/t/p/w154";

                List<SearchMovieDiscoverApiResponse.MovieResult> mockMovieResults =
                        List.of(SearchMovieDiscoverApiResponseFixture.createMovieResult(1, "title 1"),
                                SearchMovieDiscoverApiResponseFixture.createMovieResult(2, "title 2"));

                given(commonCodeService.convertCommonCodeNameToCommonCode("28", CodeGroupEnum.MOVIE_GENRE)).willReturn("01");
                given(commonCodeService.convertCommonCodeNameToCommonCode("12", CodeGroupEnum.MOVIE_GENRE)).willReturn("02");

                // when
                List<BatchDiscoverMovieResponse> process = processor.process(mockMovieResults);

                // then
                assertThat(process).hasSize(2);
                assertThat(process.get(0).genres())
                        .hasSize(2)
                        .extracting(MovieGenre::getGenreCode)
                        .containsExactlyInAnyOrder("01", "02");
                assertThat(process.get(0).movie().getBackdrop()).isEqualTo(image_path + mockMovieResults.get(0).backdrop_path());
                assertThat(process.get(0).movie().getPoster()).isEqualTo(image_path + mockMovieResults.get(0).poster_path());
                assertThat(process.get(0).movie().getTitle()).isEqualTo(mockMovieResults.get(0).title());
                assertThat(process.get(0).movie().getTmdbId()).isEqualTo(1);
            }

            @Test
            void 제목이_없는_영화_데이터_원제를_저장() {
                // given
                String image_path = "https://image.tmdb.org/t/p/w154";

                List<SearchMovieDiscoverApiResponse.MovieResult> mockMovieResults =
                        List.of(SearchMovieDiscoverApiResponseFixture.createMovieResult(1, null));

                given(commonCodeService.convertCommonCodeNameToCommonCode("28", CodeGroupEnum.MOVIE_GENRE)).willReturn("01");
                given(commonCodeService.convertCommonCodeNameToCommonCode("12", CodeGroupEnum.MOVIE_GENRE)).willReturn("02");

                // when
                List<BatchDiscoverMovieResponse> process = processor.process(mockMovieResults);

                // then
                assertThat(process).hasSize(1);
                assertThat(process.get(0).genres())
                        .hasSize(2)
                        .extracting(MovieGenre::getGenreCode)
                        .containsExactlyInAnyOrder("01", "02");
                assertThat(process.get(0).movie().getBackdrop()).isEqualTo(image_path + mockMovieResults.get(0).backdrop_path());
                assertThat(process.get(0).movie().getPoster()).isEqualTo(image_path + mockMovieResults.get(0).poster_path());
                assertThat(process.get(0).movie().getTitle()).isEqualTo(mockMovieResults.get(0).original_title());
                assertThat(process.get(0).movie().getTmdbId()).isEqualTo(1);
            }
        }
    }
}