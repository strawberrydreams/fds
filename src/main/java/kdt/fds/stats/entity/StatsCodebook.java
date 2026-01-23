package kdt.fds.stats.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 이 파일은 통계 코드북 엔티티 파일이다.
 * 타입/키 유니크 규칙이 있는 STATS_CODEBOOK 테이블을 매핑한다.
 */
@Setter
@Getter
@Entity
@Table(
        name = "STATS_CODEBOOK",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_STATS_CODEBOOK_TYPE_KEY",
                        columnNames = {"CODE_TYPE", "CODE_KEY"}
                )
        },
        indexes = {
                @Index(
                        name = "IDX_STATS_CODEBOOK_TYPE",
                        columnList = "CODE_TYPE"
                )
        }
)
public class StatsCodebook {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stats_codebook_seq")
    @SequenceGenerator(
            name = "stats_codebook_seq",
            sequenceName = "STATS_CODEBOOK_SEQ",
            allocationSize = 1
    )
    @Column(name = "CODEBOOK_ID")
    private Long codebookId;

    @Column(name = "CODE_TYPE", length = 40, nullable = false)
    private String codeType;

    @Column(name = "CODE_KEY", length = 80, nullable = false)
    private String codeKey;

    @Column(name = "DISPLAY_NAME", length = 120, nullable = false)
    private String displayName;

    @Column(name = "DESCRIPTION", length = 400)
    private String description;

    // 기본 정렬 순서를 나타내는 private 필드이다. null 입력을 피하기 위해 기본값 0을 사용한다.
    @Column(name = "SORT_ORDER")
    private Integer sortOrder = 0;

    // 엔트리 활성 여부의 기본값을 정의한다. 생성 시 기본적으로 활성 상태를 유지한다.
    @Convert(converter = YesOrNoConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    @Lob
    @Column(name = "META_JSON")
    private String metaJson;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 60)
    private String updatedBy;

    @Column(name = "CHANGE_REASON", length = 200)
    private String changeReason;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public StatsCodebook() {
    }

    /**
     * 행 삽입 전에 타임스탬프와 기본값을 초기화한다.
     * created/updated 시각과 정렬 순서가 채워지도록 한다.
     */
    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    /**
     * 행 업데이트 전에 수정 시각을 갱신한다.
     * UPDATED_AT를 최신 변경과 맞춘다.
     */
    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
