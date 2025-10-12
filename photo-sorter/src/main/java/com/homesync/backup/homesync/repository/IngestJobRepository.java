package com.homesync.backup.homesync.repository;

import com.homesync.backup.homesync.model.IngestJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestJobRepository extends JpaRepository<IngestJob, String> {
}
