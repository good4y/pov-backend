package net.pointofviews.movie.batch.discover;

import lombok.extern.slf4j.Slf4j;
import net.pointofviews.movie.batch.utils.ApiRateLimiter;
import net.pointofviews.movie.dto.response.SearchMovieDiscoverApiResponse;
import net.pointofviews.movie.service.impl.MovieTMDbSearchService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@StepScope
public class TMDbMovieDiscoverReader implements ItemReader<List<SearchMovieDiscoverApiResponse.MovieResult>> {

    private final MovieTMDbSearchService movieService;

    private final LocalDate startDate;

    private final LocalDate endDate;

    private final ApiRateLimiter batchRateLimiter;

    private final AtomicInteger currentPage = new AtomicInteger(1);

    public TMDbMovieDiscoverReader(MovieTMDbSearchService movieService, ApiRateLimiter batchRateLimiter,
                                   @Value("#{jobParameters['startDate']}") String startDateStr,
                                   @Value("#{jobParameters['endDate']}") String endDateStr) {
        this.movieService = movieService;
        this.batchRateLimiter = batchRateLimiter;
        this.startDate = LocalDate.parse(startDateStr);
        this.endDate = LocalDate.parse(endDateStr);
    }

    @Override
    public List<SearchMovieDiscoverApiResponse.MovieResult> read() {
        batchRateLimiter.limit();
        int page = currentPage.getAndIncrement();
        SearchMovieDiscoverApiResponse response = movieService.searchDiscoverMovie(startDate, endDate, page);

        if (response.results().isEmpty()) {
            return null;
        }

        int totalPages = response.total_pages();

        log.info("Fetching page {} of {}", page, totalPages);

        return response.results();
    }
}
