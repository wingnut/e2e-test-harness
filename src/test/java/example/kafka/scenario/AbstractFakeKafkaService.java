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
 * Base for test-double services that consume from a topic and produce a response
 * when a record matches a trigger prefix. Subclasses define the trigger and response.
 */
abstract class AbstractFakeKafkaService {

    private final String topic;
    private final KafkaConsumer<String, String> consumer;
    private final Producer<String, String> producer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    protected AbstractFakeKafkaService(String topic, String groupId) {
        this.topic = topic;
        this.consumer = new KafkaConsumer<>(consumerProps(groupId));
        this.producer = new KafkaProducer<>(producerProps());
        this.consumer.subscribe(List.of(topic));
    }

    /** Message value prefix this service reacts to (e.g. "order-placed:"). */
    protected abstract String getTriggerPrefix();

    /** Build the outbound message value for a matching record (key is preserved). */
    protected abstract String buildResponsePayload(ConsumerRecord<String, String> record);

    public final void start() {
        executor.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                    for (ConsumerRecord<String, String> record : records) {
                        String value = record.value();
                        if (value != null && value.startsWith(getTriggerPrefix())) {
                            String payload = buildResponsePayload(record);
                            producer.send(new ProducerRecord<>(topic, record.key(), payload));
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

    public final void stop() {
        consumer.wakeup();
        executor.shutdownNow();
    }

    private static String kafkaBootstrap() {
        return System.getProperty("KAFKA_BOOTSTRAP",
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092"));
    }

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        return props;
    }

    private static Properties consumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }
}
