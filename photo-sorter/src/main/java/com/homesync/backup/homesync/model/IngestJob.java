package com.homesync.backup.homesync.model;

import lombok.*;

import jakarta.persistence.*;
import com.homesync.backup.homesync.model.enums.JobStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingest_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestJob {
    @Id
    private String jobId;

    private String jobName;
    private String path;
    private Boolean recursive;
    private String requestedBy;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private Integer scannedCount;
    private Integer insertedCount;
    private Integer publishedCount;
    private LocalDateTime finishedAt;
}
