package net.pointofviews.movie.repository;

import net.pointofviews.curation.dto.response.ReadUserCurationMovieResponse;
import net.pointofviews.movie.domain.Movie;
import net.pointofviews.movie.dto.response.MovieTrendingResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("""
            SELECT
                m.id,
                m.title,
                m.poster,
                m.released,
                CASE WHEN :memberId IS NOT NULL AND EXISTS (SELECT 1 FROM MovieLike ml WHERE ml.movie.id = m.id AND ml.member.id = :memberId AND ml.isLiked = true) THEN true ELSE false END,
                COALESCE((SELECT mlc.likeCount FROM MovieLikeCount mlc WHERE mlc.movie.id = m.id), 0),
                COUNT(CASE WHEN r IS NOT NULL AND r.disabled = false THEN r.id ELSE NULL END)
            FROM Movie m
            LEFT JOIN m.reviews r
            GROUP BY m.id, m.title, m.poster, m.released
            ORDER BY m.released DESC
            """)
    Slice<Object[]> findAllMovies(@Param("memberId") UUID memberId, Pageable pageable);


    /**
     * 검색
     */
    @Query(value = """
            SELECT m.id, m.title, m.poster, m.released,
                    COALESCE(mlc.like_count, 0) AS movieLikeCount,
                    COUNT(DISTINCT r.id) AS movieReviewCount,
                    CASE
                        WHEN :memberId is null THEN FALSE
                        WHEN :memberId = ml.member_id THEN ml.is_liked
                        ELSE FALSE
                    END as isLiked
            FROM movie m
            LEFT JOIN movie_like_count mlc ON m.id = mlc.movie_id
            LEFT JOIN review r ON m.id = r.movie_id AND r.disabled = false
            LEFT JOIN movie_like ml ON ml.movie_id = m.id AND ml.member_id = :memberId
            WHERE m.id IN (
                SELECT DISTINCT movie_id FROM (
                    SELECT id AS movie_id FROM movie WHERE MATCH(title) AGAINST(:query IN BOOLEAN MODE)
                    UNION ALL
                    SELECT mc.movie_id FROM movie_cast mc
                    JOIN people p ON mc.people_id = p.id
                    WHERE MATCH(p.name) AGAINST(:query IN BOOLEAN MODE)
                    UNION ALL
                    SELECT mcr.movie_id FROM movie_crew mcr
                    JOIN people p ON mcr.people_id = p.id
                    WHERE MATCH(p.name) AGAINST(:query IN BOOLEAN MODE)
                ) subquery
            )
            GROUP BY m.id, m.title, m.poster, m.released, mlc.like_count, is_liked
            ORDER BY m.released DESC
            """, nativeQuery = true)
    Slice<Object[]> searchMoviesByTitleOrPeople(@Param("query") String query, @Param("memberId") UUID memberId, Pageable pageable);

    boolean existsByTmdbId(Integer id);


    @Query(value = """
            SELECT DISTINCT m.id AS id,
                   m.title AS title,
                   m.released AS released
            FROM movie m
                     LEFT JOIN movie_cast mc ON mc.movie_id = m.id
                     LEFT JOIN movie_crew mcr ON mcr.movie_id = m.id
                     LEFT JOIN people p_cast ON p_cast.id = mc.people_id
                     LEFT JOIN people p_crew ON p_crew.id = mcr.people_id
                     LEFT JOIN movie_genre mg ON mg.movie_id = m.id
                     LEFT JOIN common_code cc ON cc.code = mg.genre_code
            WHERE (:query IS NULL OR m.title LIKE CONCAT('%', :query, '%'))
               OR (:query IS NULL OR p_cast.name LIKE CONCAT('%', :query, '%'))
               OR (:query IS NULL OR p_crew.name LIKE CONCAT('%', :query, '%'))
               OR (:query IS NULL OR CAST(m.released AS CHAR) LIKE CONCAT('%', :query, '%'))
               OR (:query IS NULL OR cc.common_code_description LIKE CONCAT('%', :query, '%'))
            """, nativeQuery = true)
    Slice<Object[]> adminSearchMovies(@Param("query") String query, Pageable pageable);

    @Query("""
            SELECT new net.pointofviews.curation.dto.response.ReadUserCurationMovieResponse(
                m.title,
                m.poster,
                m.released,
                CASE WHEN :memberId IS NOT NULL AND EXISTS (SELECT 1 FROM MovieLike ml WHERE ml.movie.id = m.id AND ml.member.id = :memberId AND  ml.isLiked = true) THEN true ELSE false END,
                COALESCE((SELECT mlc.likeCount FROM MovieLikeCount mlc WHERE mlc.movie.id = m.id), 0),
                COUNT(r.id)
            )
            FROM Movie m
            LEFT JOIN m.reviews r
            WHERE m.id IN :movieIds AND r.disabled = false
            GROUP BY m.id, m.title, m.poster, m.released
            """)
    List<ReadUserCurationMovieResponse> findUserCurationMoviesByIds(@Param("movieIds") Set<Long> movieIds, @Param("memberId") UUID memberId);

    @EntityGraph(attributePaths = {"genres", "countries.country", "crews.people", "casts.people"})
    @Query("SELECT DISTINCT m FROM Movie m WHERE m.id = :movieId")
    Optional<Movie> findMovieWithDetailsById(Long movieId);

    Optional<Movie> findMovieByTmdbId(Integer tmdbId);

    @Query("""
            SELECT new net.pointofviews.movie.dto.response.MovieTrendingResponse(
                m.id,
                m.title,
                m.poster,
                m.released,
                CASE
                    WHEN :memberId IS NOT NULL AND EXISTS (
                        SELECT 1
                        FROM MovieLike ml
                        WHERE ml.movie.id = m.id AND ml.member.id = :memberId AND ml.isLiked = true
                    ) THEN true
                    ELSE false
                END,
                COALESCE((SELECT mlc.likeCount FROM MovieLikeCount mlc WHERE mlc.movie.id = m.id), 0),
                COALESCE(COUNT(r.id), 0)
            )
            FROM Movie m
            LEFT JOIN m.reviews r
            WHERE m.id IN :trendingMovieId AND (r.disabled = false OR r.id IS NULL)
            GROUP BY m.id, m.title, m.poster, m.released
            ORDER BY m.released DESC
            """)
    List<MovieTrendingResponse> findAllTrendingMovie(
            @Param("trendingMovieId") List<Long> trendingMovieId,
            @Param("memberId") UUID memberId
    );

    @Query(value = "SELECT max(m.id) FROM Movie m")
    Optional<Long> findMaxMovieId();
}
