package com.homesync.backup.homesync.service;

import com.homesync.backup.homesync.kafka.KafkaProducer;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IngestServiceImpl implements IngestService {

    private final KafkaProducer producer;
    private static final String TOPIC = "photos-to-process";

    public IngestServiceImpl(KafkaProducer producer) {
        this.producer = producer;
    }

    @Override
    public String startIngest(String path, String jobId) {
        String finalJobId = jobId != null && !jobId.isEmpty() ? jobId : UUID.randomUUID().toString();
        // For now just publish the path as message value; key is jobId
        producer.send(TOPIC, finalJobId, path);
        return finalJobId;
    }
}
