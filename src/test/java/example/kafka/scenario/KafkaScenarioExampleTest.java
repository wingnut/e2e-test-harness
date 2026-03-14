package example.kafka.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KafkaScenarioTest
@ExtendWith(KafkaTestContainerExtension.class)
class KafkaScenarioExampleTest {

    @Test
    void publishes_and_receives_string_event(Scenario scenario) {
        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("order-placed"))
                .toArriveAndVerify(event -> {
                    assertTrue(event.contains("order-placed"));
                    assertTrue(event.contains(deduplicationId));
                });
    }

    /**
     * Demonstrates andWaitForStateChange with polling: like running a SQL query repeatedly
     * until rows appear. Here we simulate a "database" (a list) that is updated asynchronously
     * after the publish; we poll until the list is non-empty, then verify its contents.
     */
    @Test
    void publish_andWaitForStateChange_pollUntilRowsAppear_thenVerify(Scenario scenario) {
        String deduplicationId = UUID.randomUUID().toString();
        // Simulate a table that gets written by an async process after the event is published
        List<String> ordersTable = new CopyOnWriteArrayList<>();

        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> ordersTable.add("order-" + deduplicationId), 200, TimeUnit.MILLISECONDS);

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForStateChange(() -> new ArrayList<>(ordersTable), rows -> !rows.isEmpty())
                .andVerify(rows -> {
                    assertEquals(1, rows.size());
                    assertTrue(rows.get(0).contains(deduplicationId));
                });
    }

    /**
     * Single-shot variant: state is available immediately (no polling).
     */
    @Test
    void publish_andWaitForStateChange_singleShot_andVerify(Scenario scenario) {
        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForStateChange(() -> "immediate-state")
                .andVerify(result -> assertEquals("immediate-state", result));
    }

    @Test
    void publish_andWaitForEventOfType_toArriveAndVerify(Scenario scenario) {
        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("order-placed"))
                .toArriveAndVerify(event -> {
                    assertTrue(event.contains("order-placed"));
                    assertTrue(event.contains(deduplicationId));
                });
    }
}

