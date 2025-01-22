package net.pointofviews.movie.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pointofviews.movie.batch.release.TMDbMovieReleaseProcessor;
import net.pointofviews.movie.service.impl.MovieGenreServiceImpl;
import net.pointofviews.movie.service.impl.MovieServiceImpl;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieDeletionListener implements StepExecutionListener {

    private final TMDbMovieReleaseProcessor processor;
    private final MovieServiceImpl movieService;
    private final MovieGenreServiceImpl movieGenreService;

    @Override
    public ExitStatus afterStep(StepExecution context) {
        String stepName = context.getStepName();
        ConcurrentLinkedQueue<Long> moviesToDelete = processor.getMoviesToDelete();
        List<Long> ids = moviesToDelete.stream().toList();

        movieGenreService.deleteAllByMovieIds(ids);
        movieService.deleteAllMovies(ids);
        moviesToDelete.clear();

        log.info("[Step: {}] 삭제 된 Item 갯수: {}", stepName, ids.size());
        return context.getExitStatus();
    }
}