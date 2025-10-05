package com.homesync.backup.homesync.repository;

import com.homesync.backup.homesync.model.SamplePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SamplePhotoRepository extends JpaRepository<SamplePhoto, Long> {
}
