package com.homesync.backup.homesync.controller;

import com.homesync.backup.homesync.model.SamplePhoto;
import com.homesync.backup.homesync.service.SamplePhotoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/photos")
public class SamplePhotoController {

    private final SamplePhotoService service;

    public SamplePhotoController(SamplePhotoService service) {
        this.service = service;
    }

    @GetMapping
    public List<SamplePhoto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SamplePhoto> get(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SamplePhoto> create(@RequestBody SamplePhoto photo) {
        SamplePhoto created = service.create(photo);
        return ResponseEntity.created(URI.create("/api/photos/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SamplePhoto> update(@PathVariable Long id, @RequestBody SamplePhoto photo) {
        try {
            SamplePhoto updated = service.update(id, photo);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
