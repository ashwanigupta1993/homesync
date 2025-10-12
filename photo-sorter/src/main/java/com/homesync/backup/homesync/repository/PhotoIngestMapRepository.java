package com.homesync.backup.homesync.repository;

import com.homesync.backup.homesync.model.PhotoIngestMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PhotoIngestMapRepository extends JpaRepository<PhotoIngestMap, Long> {
}
