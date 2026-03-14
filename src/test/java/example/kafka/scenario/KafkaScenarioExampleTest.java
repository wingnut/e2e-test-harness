package example.kafka.scenario;

import example.kafka.scenario.junit.KafkaScenarioTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
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

    /**
     * Longer flow: order-placed → FakeOrderService → order-persisted → FakeShipmentService → shipment-created.
     * Test publishes only the first event and waits for the final one.
     */
    @Test
    void order_placed_leads_to_order_persisted_then_shipment_created(Scenario scenario) {
        FakeOrderService orderService = new FakeOrderService(scenario.topicName());
        FakeShipmentService shipmentService = new FakeShipmentService(scenario.topicName());
        orderService.start();
        shipmentService.start();

        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created"))
                .toArriveAndVerify(event ->
                        assertTrue(event.contains("shipment-created") && event.contains(deduplicationId)));

        orderService.stop();
        shipmentService.stop();
    }

    /**
     * Illustrates {@link Scenario#stimulate(Runnable)}: run an action as part of the scenario
     * (e.g. start services) before publishing and waiting. Here we start the fakes inside
     * stimulate, then publish and wait for the final event.
     */
    @Test
    void stimulate_runs_action_before_publish_then_wait(Scenario scenario) {
        FakeOrderService orderService = new FakeOrderService(scenario.topicName());
        FakeShipmentService shipmentService = new FakeShipmentService(scenario.topicName());

        scenario
                .stimulate(() -> {
                    orderService.start();
                    shipmentService.start();
                })
                .publish(UUID.randomUUID().toString(), "order-placed:triggered-by-stimulate")
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created"))
                .toArriveAndVerify(event -> assertTrue(event.contains("shipment-created")));

        orderService.stop();
        shipmentService.stop();
    }

    /**
     * Stimulate then wait for event (no publish in the chain). The stimulus starts the fakes
     * and publishes an event; the scenario then waits for the resulting shipment-created.
     */
    @Test
    void stimulate_then_andWaitForEventOfType(Scenario scenario) {
        FakeOrderService orderService = new FakeOrderService(scenario.topicName());
        FakeShipmentService shipmentService = new FakeShipmentService(scenario.topicName());
        String orderId = UUID.randomUUID().toString();

        scenario
                .stimulate(() -> {
                    orderService.start();
                    shipmentService.start();
                    scenario.publish(orderId, "order-placed:" + orderId);
                })
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created") && s.contains(orderId))
                .toArriveAndVerify(event -> assertTrue(event.contains("shipment-created") && event.contains(orderId)));

        orderService.stop();
        shipmentService.stop();
    }

    /**
     * Stimulate then wait for state change (no publish). The stimulus schedules a "batch job"
     * that writes a result; the scenario then polls until that result appears.
     */
    @Test
    void stimulate_then_andWaitForStateChange(Scenario scenario) {
        List<String> batchResult = new CopyOnWriteArrayList<>();

        scenario
                .stimulate(() -> Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> batchResult.add("done"), 150, TimeUnit.MILLISECONDS))
                .andWaitForStateChange(() -> new ArrayList<>(batchResult), list -> !list.isEmpty())
                .andVerify(list -> assertEquals("done", list.get(0)));
    }

    /**
     * Illustrates {@link Scenario#stimulateParallel(Runnable...)}: "poke at the system" with
     * multiple actions in parallel (e.g. upload files to S3, call external APIs) before
     * the main scenario flow. Here we simulate an upload bucket: one file first, then
     * two more in parallel, then publish and wait for the outcome.
     */
    @Test
    void stimulateParallel_pokes_at_system_in_parallel_then_single_flow(Scenario scenario) {
        // Simulate something the system under test might depend on (e.g. S3 uploads)
        List<String> uploadedKeys = new CopyOnWriteArrayList<>();

        FakeOrderService orderService = new FakeOrderService(scenario.topicName());
        FakeShipmentService shipmentService = new FakeShipmentService(scenario.topicName());
        orderService.start();
        shipmentService.start();

        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .stimulate(() -> uploadedKeys.add("invoices/order-" + deduplicationId + ".pdf"))
                .stimulateParallel(
                        () -> uploadedKeys.add("packing-slips/order-" + deduplicationId + ".pdf"),
                        () -> uploadedKeys.add("labels/order-" + deduplicationId + ".pdf")
                )
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created"))
                .toArriveAndVerify(event -> {
                    assertTrue(event.contains("shipment-created") && event.contains(deduplicationId));
                    assertEquals(3, uploadedKeys.size(), "All three uploads should have completed before we asserted");
                });

        orderService.stop();
        shipmentService.stop();
    }

    /**
     * Illustrates {@link Scenario#withTimeout(Duration)}: override the default wait timeout
     * for this scenario (e.g. when the flow is slow). Here we set a longer timeout and run
     * the full order→shipment flow to show the API.
     */
    @Test
    void withTimeout_overrides_default_wait_timeout(Scenario scenario) {
        FakeOrderService orderService = new FakeOrderService(scenario.topicName());
        FakeShipmentService shipmentService = new FakeShipmentService(scenario.topicName());
        orderService.start();
        shipmentService.start();

        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .withTimeout(Duration.ofSeconds(60))
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created"))
                .toArriveAndVerify(event -> assertTrue(event.contains("shipment-created") && event.contains(deduplicationId)));

        orderService.stop();
        shipmentService.stop();
    }

    /**
     * Illustrates {@link AwaitingEvent#withDeduplicationId(String)}: explicitly filter
     * the awaited event by deduplication id (e.g. when correlating with a specific flow).
     */
    @Test
    void andWaitForEventOfType_withDeduplicationId_filters_by_key(Scenario scenario) {
        String deduplicationId = UUID.randomUUID().toString();

        scenario
                .publish(deduplicationId, "order-placed:" + deduplicationId)
                .andWaitForEventOfType(String.class)
                .withDeduplicationId(deduplicationId)
                .matching(s -> s.contains("order-placed"))
                .toArriveAndVerify(event -> {
                    assertTrue(event.contains("order-placed"));
                    assertTrue(event.contains(deduplicationId));
                });
    }

    /**
     * Flow: (1) order-placed → await order-persisted, (2) POKE to run a batch job,
     * (3) await state change (poll until batch result), (4) new publish → await shipment-created.
     * Shows using stimulate in between two publish→await flows.
     */
    @Test
    void stimulate_between_two_publish_await_flows(Scenario scenario) {
        FakeOrderService orderService = new FakeOrderService(scenario.topicName());
        FakeShipmentService shipmentService = new FakeShipmentService(scenario.topicName());
        orderService.start();
        shipmentService.start();

        String orderId1 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();

        // Something the "batch job" writes when done (e.g. a table or file the test polls)
        List<String> batchJobResults = new CopyOnWriteArrayList<>();

        // (1) First flow: order-placed → await order-persisted
        scenario
                .publish(orderId1, "order-placed:" + orderId1)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("order-persisted") && s.contains(orderId1))
                .toArriveAndVerify(event -> assertTrue(event.contains("order-persisted")));

        // (2) POKE: run a batch job (simulated – e.g. triggers async processor that writes when done)
        scenario.stimulate(() ->
                Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> batchJobResults.add("batch-done"), 200, TimeUnit.MILLISECONDS));

        // (3) Await state change: poll until something is watching the batch job / table
        scenario
                .publish(orderId1, "batch-trigger")
                .andWaitForStateChange(() -> new ArrayList<>(batchJobResults), list -> !list.isEmpty())
                .andVerify(results -> assertEquals("batch-done", results.get(0)));

        // (4) New publish and await: second order → await shipment-created
        scenario
                .publish(orderId2, "order-placed:" + orderId2)
                .andWaitForEventOfType(String.class)
                .matching(s -> s.contains("shipment-created") && s.contains(orderId2))
                .toArriveAndVerify(event -> assertTrue(event.contains("shipment-created") && event.contains(orderId2)));

        orderService.stop();
        shipmentService.stop();
    }
}

