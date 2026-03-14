package example.kafka.scenario;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple test-local "order service" that reacts to "order-placed" events
 * and emits "shipment-created" events to the same topic.
 *
 * This is intentionally minimal and only meant to demonstrate how the
 * KafkaScenario harness can be used for richer end-to-end style tests.
 */
class OrderService {

    private final String topic;
    private final KafkaConsumer<String, String> consumer;
    private final Producer<String, String> producer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    OrderService(String topic) {
        this.topic = topic;
        this.consumer = new KafkaConsumer<>(consumerProps());
        this.producer = new KafkaProducer<>(producerProps());
        this.consumer.subscribe(List.of(topic));
    }

    void start() {
        executor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records =
                            consumer.poll(Duration.ofMillis(200));
                    for (ConsumerRecord<String, String> record : records) {
                        String value = record.value();
                        if (value != null && value.startsWith("order-placed:")) {
                            String deduplicationId = record.key();
                            String payload = "shipment-created:" + deduplicationId;
                            producer.send(new ProducerRecord<>(topic, deduplicationId, payload));
                        }
                    }
                }
            } catch (WakeupException ignored) {
            } finally {
                consumer.close();
                producer.close();
            }
        });
    }

    void stop() {
        consumer.wakeup();
        executor.shutdownNow();
    }

    private static String kafkaBootstrap() {
        return System.getProperty("KAFKA_BOOTSTRAP",
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092"));
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        return props;
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }
}

