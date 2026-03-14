package example.kafka.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

@KafkaScenarioTest
@ExtendWith(KafkaTestContainerExtension.class)
class KafkaScenarioExampleTest {

    @Test
    void publishes_and_receives_string_event(Scenario scenario) {
        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .stimulate(() ->
                        scenario.publish(deduplicationId, "order-placed:" + deduplicationId))
                .expect(String.class)
                .withDeduplicationId(deduplicationId)
                .matching(s -> s.contains("order-placed"))
                .times(1);

        scenario.verify();
    }
}

