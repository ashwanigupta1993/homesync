package com.homesync.backup.homesync.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/callback")
public class CallbackController {

    // For now, accept a generic payload and log it. The real implementation will
    // deserialize into a DTO and call a service to persist person/photo mappings.

    @PostMapping("/photo-processed")
    public ResponseEntity<String> photoProcessed(@RequestBody Map<String, Object> payload) {
        // TODO: call CallbackService to persist results (idempotent)
        System.out.println("Callback received: " + payload);
        return ResponseEntity.ok("ok");
    }
}
