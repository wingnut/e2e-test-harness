package example.kafka.scenario.internal;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Internal: consumes from the scenario topic and records events into {@link ScenarioEventStore}.
 * Not part of the public API.
 */
public final class ScenarioKafkaConsumer {

    private final KafkaConsumer<String, String> consumer;
    private final ScenarioEventStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ScenarioKafkaConsumer(Properties props, ScenarioEventStore store, String topic) {
        this.consumer = new KafkaConsumer<>(props);
        this.store = store;
        consumer.subscribe(List.of(topic));
    }

    public void start() {
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

    public void stop() {
        consumer.wakeup();
        executor.shutdownNow();
    }
}
