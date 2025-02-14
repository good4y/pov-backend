package net.pointofviews.movie.batch.discover;

import net.pointofviews.config.TestContainerInitializer;
import net.pointofviews.movie.domain.Movie;
import net.pointofviews.movie.domain.MovieGenre;
import net.pointofviews.movie.dto.response.BatchDiscoverMovieResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.jdbc.JdbcTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@SpringBatchTest
@ImportTestcontainers(TestContainerInitializer.class)
class TMDbMovieDiscoverWriterTest {

    @Autowired
    private TMDbMovieDiscoverWriter writer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 배치_작업이_정상적으로_실행되고_데이터가_삽입된다() {
        // Given
        BatchDiscoverMovieResponse response = createBatchDiscoverMovieResponse();

        // When
        writer.write(new Chunk<>(List.of(List.of(response))));

        // Then
        int movieCount = JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "movie", "tmdb_id = 12345");
        assertThat(movieCount).isEqualTo(1);

        int genreCount = JdbcTestUtils.countRowsInTable(jdbcTemplate, "movie_genre");
        assertThat(genreCount).isEqualTo(2);
    }

    @Test
    void 중복된_TMDb_ID_삽입시_DuplicateKeyException_발생() {
        // given
        BatchDiscoverMovieResponse response = createBatchDiscoverMovieResponse();

        // when
        writer.write(new Chunk<>(List.of(List.of(response, response))));

        // then
        int movieCount = JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "movie", "tmdb_id = " + response.movie().getTmdbId());
        assertThat(movieCount).isEqualTo(1);
    }

    private BatchDiscoverMovieResponse createBatchDiscoverMovieResponse() {
        Movie testMovie = Movie.builder()
                .title("Test Movie")
                .plot("Test Plot")
                .poster("Test Poster")
                .backdrop("Test Backdrop")
                .tmdbId(12345)
                .released(LocalDate.of(2024, 1, 1))
                .build();

        MovieGenre genre1 = MovieGenre.builder().movie(testMovie).genreCode("01").build();
        MovieGenre genre2 = MovieGenre.builder().movie(testMovie).genreCode("02").build();

        return new BatchDiscoverMovieResponse(testMovie, List.of(genre1, genre2));
    }
}
