package kdt.fds.common.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "FDS_CONFIG") // schema 제거
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FdsConfig {
    @Id
    @Column(name = "CONFIG_KEY")
    private String configKey;

    @Column(name = "CONFIG_VALUE")
    private String configValue;

    @Column(name = "DESCRIPTION")
    private String description;
}