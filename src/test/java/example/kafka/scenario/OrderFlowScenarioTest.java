package example.kafka.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KafkaScenarioTest
@ExtendWith(KafkaTestContainerExtension.class)
class OrderFlowScenarioTest {

    @Test
    void order_leads_to_shipment_event(Scenario scenario) {
        OrderService service = new OrderService(scenario.topicName());
        service.start();

        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created"))
                .toArriveAndVerify(event ->
                        assertTrue(event.contains("shipment-created") && event.contains(deduplicationId)));

        service.stop();
    }
}

