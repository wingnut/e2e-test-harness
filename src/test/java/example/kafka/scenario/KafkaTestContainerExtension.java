package example.kafka.scenario;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit 5 extension that starts an Apache Kafka (native) Testcontainer and exposes its
 * bootstrap servers via the {@code KAFKA_BOOTSTRAP} system property so
 * that {@link example.kafka.scenario.junit.KafkaScenarioExtension} will connect to it.
 * Uses {@code apache/kafka-native} for faster startup (~2–3 s) than JVM-based images.
 */
public class KafkaTestContainerExtension implements BeforeAllCallback {

    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
        System.setProperty("KAFKA_BOOTSTRAP", KAFKA.getBootstrapServers());
    }
}

