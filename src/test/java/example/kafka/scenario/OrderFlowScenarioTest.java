package example.kafka.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

@KafkaScenarioTest
@ExtendWith(KafkaTestContainerExtension.class)
class OrderFlowScenarioTest {

    @Test
    void order_leads_to_shipment_event(Scenario scenario) {
        OrderService service = new OrderService(scenario.topicName());
        service.start();

        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .stimulate(() ->
                        scenario.publish(deduplicationId, "order-placed:" + deduplicationId))
                .expect(String.class)
                .withDeduplicationId(deduplicationId)
                .matching(s -> s.contains("shipment-created"))
                .times(1);

        scenario.verify();
        service.stop();
    }
}

