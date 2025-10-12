package com.homesync.backup.homesync.model;

import lombok.*;

import jakarta.persistence.*;
import com.homesync.backup.homesync.model.enums.MapStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo_ingest_map")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoIngestMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mapId;

    private Long photoId;
    private Long batchId;
    private String jobId;

    private LocalDateTime insertedAt;
    private Integer publishAttempts;
    private String lastPublishError;

    @Enumerated(EnumType.STRING)
    private MapStatus status;
}
