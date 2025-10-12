package com.homesync.backup.homesync.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.photos-to-process:photos-to-process}")
    private String photosToProcessTopic;

    @Bean
    public NewTopic photosToProcess() {
        return new NewTopic(photosToProcessTopic, 3, (short) 1);
    }
}
