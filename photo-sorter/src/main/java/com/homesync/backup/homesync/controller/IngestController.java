package com.homesync.backup.homesync.controller;

import com.homesync.backup.homesync.dto.IngestRequest;
import com.homesync.backup.homesync.service.IngestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/folder")
    public ResponseEntity<String> ingestFolder(@RequestBody IngestRequest request) {
        String jobId = ingestService.startIngest(request.getPath(), request.getJobId());
        return ResponseEntity.ok(jobId);
    }
}
