package com.homesync.backup.homesync.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.UUID;

public class ContinuousProducer {
    public static void main(String[] args) {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Add client ID for better logging
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "continuous-producer-" + System.currentTimeMillis());
        // Add acks=all for stronger delivery guarantees
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        
        System.out.println("Starting producer with:");
        System.out.println("Bootstrap servers: " + bootstrap);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String topic = "test-topic";
            System.out.println("Started continuous producer. Press Ctrl+C to stop.");
            System.out.println("Publishing to topic: " + topic);
            
            while (true) {
                String key = UUID.randomUUID().toString();
                String value = String.format("test-message-%d", System.currentTimeMillis());
                final String msgKey = key;  // Final copy for lambda
                
                try {
                    producer.send(new ProducerRecord<>(topic, key, value),
                        (metadata, exception) -> {
                            if (exception != null) {
                                System.err.println("Error sending message: " + exception.getMessage());
                                exception.printStackTrace();
                            } else {
                                System.out.printf("Successfully sent message: key=%s, partition=%d, offset=%d%n",
                                    msgKey, metadata.partition(), metadata.offset());
                            }
                        }).get(); // Wait for send to complete
                } catch (Exception e) {
                    System.err.println("Failed to send message: " + e.getMessage());
                    e.printStackTrace();
                }
                
                Thread.sleep(1000); // Sleep for 1 second between messages
            }
        } catch (InterruptedException e) {
            System.out.println("Producer interrupted, shutting down.");
            Thread.currentThread().interrupt();
        }
    }
}