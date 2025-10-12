package com.homesync.backup.homesync.model;

import lombok.*;

import jakarta.persistence.*;
import com.homesync.backup.homesync.model.enums.PhotoStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1024)
    private String path;

    @Column(nullable = false, length = 512)
    private String filename;

    private Long filesize;

    private Integer width;
    private Integer height;

    private LocalDateTime insertedAt;

    private LocalDateTime modifiedAt;

    @Enumerated(EnumType.STRING)
    private PhotoStatus status;

    @Column(columnDefinition = "json")
    private String personIds;
}
