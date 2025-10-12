package com.homesync.backup.homesync.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class TestKafkaProducerTest {

    @Test
    public void testProduceMessage() {
        assertDoesNotThrow(() -> {
            String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                String topic = "test-topic";
                String key = UUID.randomUUID().toString();
                String value = "test-" + System.currentTimeMillis();
                producer.send(new ProducerRecord<>(topic, key, value)).get();
                // short sleep to allow broker to process
                Thread.sleep(Duration.ofMillis(200).toMillis());
            }
        });
    }
}
