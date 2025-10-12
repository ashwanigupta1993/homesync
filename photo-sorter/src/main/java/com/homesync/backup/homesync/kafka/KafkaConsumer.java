package com.homesync.backup.homesync.kafka;

import com.homesync.backup.homesync.service.PhotoWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {

    private final PhotoWorkerService workerService;

    public KafkaConsumer(PhotoWorkerService workerService) {
        this.workerService = workerService;
    }

    @KafkaListener(topics = "photos-to-process", groupId = "photo-sorter-group")
    public void listen(String message) {
        // message currently contains the path; in future we may accept JSON with path+meta
        System.out.println("Received message (path): " + message);
        // For now call worker with no callbackUrl (worker uses default)
        workerService.processPath(null, message, null);
    }
}
