package net.pointofviews.movie.batch.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.movie.exception.MovieException;
import net.pointofviews.movie.repository.MovieRepository;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class MoviePartitioner implements Partitioner {

    private final Long startMoviePk;
    private final MovieRepository movieRepository;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        long min = startMoviePk;
        long max = movieRepository.findMaxMovieId().orElseThrow(
                () -> new MovieException(HttpStatus.NOT_FOUND, "영화가 존재하지 않습니다."));
        long targetSize = (max - min) / gridSize + 1;

        Map<String, ExecutionContext> result = new HashMap<>();
        long number = 1;
        long start = min;
        long end = start + targetSize - 1;

        while (start <= max) {
            ExecutionContext value = new ExecutionContext();
            result.put("partition" + number, value);

            if (end >= max) {
                end = max;
            }

            value.putLong("minId", start);
            value.putLong("maxId", end);
            start += targetSize;
            end += targetSize;
            number++;
        }

        return result;
    }
}
