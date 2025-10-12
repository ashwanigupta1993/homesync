package com.homesync.backup.homesync.model;

import lombok.*;

import jakarta.persistence.*;

@Entity
@Table(name = "photo_persons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoPerson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long photoId;
    private Long personId;
}
