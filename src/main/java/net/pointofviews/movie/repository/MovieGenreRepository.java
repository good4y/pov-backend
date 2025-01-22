package net.pointofviews.movie.repository;

import net.pointofviews.movie.domain.MovieGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, Long> {
    @Modifying
    @Query("DELETE FROM MovieGenre mg WHERE mg.movie.id IN :movieIds")
    void deleteByMovieIds(@Param("movieIds") List<Long> movieIds);
}