package net.pointofviews.movie.batch.release;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.movie.batch.utils.MoviePartitioner;
import net.pointofviews.movie.domain.Movie;
import net.pointofviews.movie.repository.MovieRepository;
import net.pointofviews.movie.service.impl.MovieGenreServiceImpl;
import net.pointofviews.movie.service.impl.MovieServiceImpl;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TMDbMovieReleaseStep {

    private final JobRepository jobRepository;
    private final JpaPagingItemReader<Movie> movieReleaseJpaReader;
    private final TMDbMovieReleaseProcessor processor;
    private final TMDbMovieReleaseWriter writer;
    private final PlatformTransactionManager transactionManager;
    private final MovieRepository movieRepository;
    private final MovieServiceImpl movieService;
    private final MovieGenreServiceImpl movieGenreService;

    @Bean
    public Step tmdbMovieReleaseStep() {
        return new StepBuilder("tmdbMovieReleaseStep", jobRepository)
                .<Movie, Movie>chunk(100, transactionManager)
                .reader(movieReleaseJpaReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public TaskExecutorPartitionHandler releasePartitionHandler(TaskExecutor tmdbTaskExecutor) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(tmdbMovieReleaseStep());
        partitionHandler.setTaskExecutor(tmdbTaskExecutor);
        partitionHandler.setGridSize(8);
        return partitionHandler;
    }

    @Bean
    public Step tmdbMoviePartitionedStep(TaskExecutor tmdbTaskExecutor) {
        return new StepBuilder("tmdbMoviePartitionedStep", jobRepository)
                .partitioner("tmdbMovieReleaseStep", moviePartitioner(null))
                .step(tmdbMovieReleaseStep())
                .partitionHandler(releasePartitionHandler(tmdbTaskExecutor))
                .build();
    }

    @Bean
    @StepScope
    public MoviePartitioner moviePartitioner(
            @Value("#{jobParameters['startMoviePk']}") String startMoviePk) {

        return new MoviePartitioner(Long.parseLong(startMoviePk), movieRepository);
    }

    @Bean
    public Step cleanupStep() {
        return new StepBuilder("cleanupStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    ConcurrentLinkedQueue<Long> moviesToDelete = processor.getMoviesToDelete();
                    List<Long> ids = moviesToDelete.stream().toList();

                    movieGenreService.deleteAllByMovieIds(ids);
                    movieService.deleteAllMovies(ids);
                    moviesToDelete.clear();

                    log.info("삭제 된 Item 갯수: {}", ids.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

}
