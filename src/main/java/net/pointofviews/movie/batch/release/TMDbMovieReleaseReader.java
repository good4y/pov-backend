package net.pointofviews.movie.batch.release;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.movie.domain.Movie;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TMDbMovieReleaseReader {

    @Bean
    @StepScope
    public JpaPagingItemReader<Movie> movieReleaseJpaReader(EntityManagerFactory entityManagerFactory,
                                                            @Value("#{stepExecutionContext['minId']}") Long startMoviePk,
                                                            @Value("#{stepExecutionContext['maxId']}") Long endMoviePk) {
        JpaPagingItemReader<Movie> reader = new JpaPagingItemReader<>();

        reader.setEntityManagerFactory(entityManagerFactory);
        reader.setQueryString("SELECT m FROM Movie m WHERE m.id BETWEEN :startMoviePk AND :endMoviePk");
        reader.setParameterValues(Map.of("startMoviePk", startMoviePk, "endMoviePk", endMoviePk));
        reader.setPageSize(100);

        log.info("PK 범위로 영화 데이터를 읽습니다: startMoviePk={}, endMoviePk={}", startMoviePk, endMoviePk);
        return reader;
    }

}
