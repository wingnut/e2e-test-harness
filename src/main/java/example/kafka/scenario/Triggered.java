package example.kafka.scenario;

import org.awaitility.Awaitility;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Stage of the scenario DSL after something was triggered (e.g. {@link Scenario#publish(String, String)}).
 * Await an outcome: event or state change. Mimics Modulith's post-publish API.
 */
public final class Triggered {

    private final Scenario scenario;
    private final String deduplicationId;

    Triggered(Scenario scenario, String deduplicationId) {
        this.scenario = scenario;
        this.deduplicationId = deduplicationId;
    }

    /**
     * Repeatedly evaluate {@code stateSupplier} (e.g. a SQL query) until {@code until}
     * returns true, then verify the result. Use this when some async process (triggered
     * by the publish) eventually updates a database and you want to poll until rows appear.
     * <p>
     * Example: publish an order event, then wait until a DB query returns the new row:
     * <pre>
     * scenario.publish(id, event)
     *   .andWaitForStateChange(
     *     () -> jdbcTemplate.query("SELECT * FROM orders WHERE id = ?", rowMapper, id),
     *     rows -> !rows.isEmpty())
     *   .andVerify(rows -> assertEquals(1, rows.size()));
     * </pre>
     */
    public <T> ObservedState<T> andWaitForStateChange(Supplier<T> stateSupplier, Predicate<T> until) {
        AtomicReference<T> result = new AtomicReference<>();
        Awaitility.await()
                .atMost(scenario.timeout())
                .until(() -> {
                    T value = stateSupplier.get();
                    if (until.test(value)) {
                        result.set(value);
                        return true;
                    }
                    return false;
                });
        return new ObservedState<>(result.get());
    }

    /**
     * Run a state supplier once and verify the result. Use when the supplier already
     * blocks until state is ready (e.g. a future.get()) or state is immediately available.
     */
    public <T> ObservedState<T> andWaitForStateChange(Supplier<T> stateChange) {
        T result = stateChange.get();
        return new ObservedState<>(result);
    }

    /**
     * Wait for an event of the given type (optionally filtered by deduplication id and predicate),
     * then verify it. Mimics Modulith's {@code andWaitForEventOfType(SomeOtherEvent.class).matching(...).toArriveAndVerify(...)}.
     */
    public <T> AwaitingEvent<T> andWaitForEventOfType(Class<T> type) {
        return new AwaitingEvent<>(scenario, deduplicationId, type);
    }
}
