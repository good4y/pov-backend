package net.pointofviews.movie.service;

import java.util.List;

public interface MovieGenreService {
    void deleteAllByMovieIds(List<Long> movieId);
}