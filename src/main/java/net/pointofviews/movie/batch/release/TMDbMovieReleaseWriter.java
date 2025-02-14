package net.pointofviews.movie.batch.release;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.movie.domain.Movie;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TMDbMovieReleaseWriter implements ItemWriter<Movie> {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void write(Chunk<? extends Movie> chunk) {
        String sql = "UPDATE movie SET released = ?, film_rating = ? WHERE id = ?";

        List<Object[]> batchArgs = chunk.getItems().parallelStream()
                .map(movie -> new Object[]{
                        movie.getReleased(),
                        movie.getFilmRating().getTmdbCode(),
                        movie.getId()
                })
                .toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);
        log.info("저장된 아이템 갯수 {}", chunk.getItems().size());
    }
}
