package com.homesync.backup.homesync.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class ContinuousConsumer {
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        // Use a fixed group ID to maintain offset position between runs
        String group = "fixed-consumer-group";
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Set to earliest to read all messages if no offset is committed
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Enable auto commit so offsets are saved
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // Commit offsets every second
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        
        System.out.println("Starting consumer with:");
        System.out.println("Bootstrap servers: " + bootstrap);
        System.out.println("Group ID: " + group);
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down consumer...");
                running = false;
                consumer.wakeup();
            }));
            
            // Subscribe to topic
            consumer.subscribe(Collections.singletonList("test-topic"));
            System.out.println("Subscribed to test-topic. Press Ctrl+C to stop.");
            
            try {
                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (!records.isEmpty()) {
                        System.out.printf("Received %d records%n", records.count());
                        records.forEach(record -> 
                            System.out.printf("Received message: key=%s, value=%s, partition=%d, offset=%d%n",
                                record.key(), record.value(), record.partition(), record.offset()));
                    }
                }
            } catch (WakeupException e) {
                System.out.println("Consumer received shutdown signal");
            } finally {
                consumer.close();
                System.out.println("Consumer closed.");
            }
        }
    }
}
