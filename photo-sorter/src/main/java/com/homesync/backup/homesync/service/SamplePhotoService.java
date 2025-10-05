package com.homesync.backup.homesync.service;

import com.homesync.backup.homesync.model.SamplePhoto;
import com.homesync.backup.homesync.repository.SamplePhotoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SamplePhotoService {

    private final SamplePhotoRepository repository;

    public SamplePhotoService(SamplePhotoRepository repository) {
        this.repository = repository;
    }

    public List<SamplePhoto> findAll() {
        return repository.findAll();
    }

    public Optional<SamplePhoto> findById(Long id) {
        return repository.findById(id);
    }

    public SamplePhoto create(SamplePhoto photo) {
        return repository.save(photo);
    }

    public SamplePhoto update(Long id, SamplePhoto update) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setFilename(update.getFilename());
                    return repository.save(existing);
                })
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + id));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
