package example.kafka.scenario;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ScenarioKafkaConsumer {

    private final KafkaConsumer<String, String> consumer;
    private final ScenarioEventStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    ScenarioKafkaConsumer(Properties props, ScenarioEventStore store, String topic) {
        this.consumer = new KafkaConsumer<>(props);
        this.store = store;
        consumer.subscribe(List.of(topic));
    }

    void start() {
        executor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                    for (ConsumerRecord<String, String> r : records) {
                        store.record(r.topic(), r.key(), r.value());
                    }
                }
            } catch (WakeupException ignored) {
            } finally {
                consumer.close();
            }
        });
    }

    void stop() {
        consumer.wakeup();
        executor.shutdownNow();
    }
}

