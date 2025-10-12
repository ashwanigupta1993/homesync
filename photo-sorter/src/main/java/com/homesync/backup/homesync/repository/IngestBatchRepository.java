package com.homesync.backup.homesync.repository;

import com.homesync.backup.homesync.model.IngestBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestBatchRepository extends JpaRepository<IngestBatch, Long> {
}
