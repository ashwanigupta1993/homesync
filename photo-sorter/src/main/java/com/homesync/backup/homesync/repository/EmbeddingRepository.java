package com.homesync.backup.homesync.repository;

import com.homesync.backup.homesync.model.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {
}
