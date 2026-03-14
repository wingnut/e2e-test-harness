package example.kafka.scenario;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit 5 extension that starts a Confluent Kafka Testcontainer and exposes its
 * bootstrap servers via the {@code KAFKA_BOOTSTRAP} system property so
 * that {@link KafkaScenarioExtension} will connect to it.
 */
public class KafkaTestContainerExtension implements BeforeAllCallback {

    private static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
        System.setProperty("KAFKA_BOOTSTRAP", KAFKA.getBootstrapServers());
    }
}

