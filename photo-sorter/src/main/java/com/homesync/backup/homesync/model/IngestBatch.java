package com.homesync.backup.homesync.model;

import lombok.*;

import jakarta.persistence.*;
import com.homesync.backup.homesync.model.enums.BatchStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingest_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long batchId;

    private String jobId;
    private Integer batchIndex;
    private Integer photoCount;

    @Enumerated(EnumType.STRING)
    private BatchStatus status;

    private LocalDateTime insertedAt;
    private LocalDateTime publishedAt;
}
