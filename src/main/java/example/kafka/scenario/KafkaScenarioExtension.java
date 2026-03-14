package example.kafka.scenario;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.Properties;
import java.util.UUID;

/**
 * JUnit 5 extension that provisions a per-test Kafka topic, producer and
 * internal consumer-backed {@link ScenarioEventStore}, then injects a
 * {@link Scenario} instance into test methods.
 */
public class KafkaScenarioExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private Scenario scenario;
    private ScenarioKafkaConsumer consumer;

    @Override
    public void beforeEach(ExtensionContext context) {
        ScenarioEventStore store = new ScenarioEventStore();
        String topic = "scenario-" + UUID.randomUUID();

        Producer<String, String> producer = new KafkaProducer<>(producerProps());

        consumer = new ScenarioKafkaConsumer(consumerProps(), store, topic);
        consumer.start();

        scenario = new Scenario(producer, store, topic);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        consumer.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(Scenario.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        return scenario;
    }

    private Properties producerProps() {
        Properties props = new Properties();
        String bootstrap = System.getProperty(
                "KAFKA_BOOTSTRAP",
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092")
        );
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        return props;
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        String bootstrap = System.getProperty(
                "KAFKA_BOOTSTRAP",
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092")
        );
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "scenario-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }
}

