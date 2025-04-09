package net.pointofviews.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberFavorGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @Column(length = 2)
    private String genreCode;

    @Builder
    private MemberFavorGenre(String genreCode, Member member) {
        this.genreCode = genreCode;
        this.member = member;
    }
}
