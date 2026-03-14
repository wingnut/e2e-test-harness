package example.kafka.scenario.junit;

import example.kafka.scenario.Scenario;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JUnit 5 annotation to enable the KafkaScenario extension and inject
 * {@link Scenario} into test methods, mimicking Spring Modulith's
 * Scenario-based testing style.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KafkaScenarioExtension.class)
public @interface KafkaScenarioTest {
}
