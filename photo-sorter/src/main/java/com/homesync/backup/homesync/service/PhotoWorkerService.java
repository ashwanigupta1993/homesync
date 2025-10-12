package com.homesync.backup.homesync.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class PhotoWorkerService {

    private final RestTemplate rest = new RestTemplate();
    // In a real setup, make this configurable
    private final String mlServiceUrl = "http://localhost:8000/process-photo";

    public void processPath(String jobId, String path, String callbackUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("path", path);
        body.put("callbackUrl", callbackUrl);
        body.put("jobId", jobId);

        try {
            rest.postForEntity(mlServiceUrl, body, String.class);
        } catch (Exception e) {
            // log and handle retries/alerts in future
            System.err.println("Failed to call ML service: " + e.getMessage());
        }
    }
}
