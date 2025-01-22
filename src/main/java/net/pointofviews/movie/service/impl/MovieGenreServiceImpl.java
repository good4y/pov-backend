package net.pointofviews.movie.service.impl;

import lombok.RequiredArgsConstructor;
import net.pointofviews.movie.repository.MovieGenreRepository;
import net.pointofviews.movie.service.MovieGenreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MovieGenreServiceImpl implements MovieGenreService {

    private final MovieGenreRepository movieGenreRepository;

    @Override
    public void deleteAllByMovieIds(List<Long> movieId) {
        movieGenreRepository.deleteByMovieIds(movieId);
    }
}