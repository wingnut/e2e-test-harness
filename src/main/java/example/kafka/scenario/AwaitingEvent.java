package example.kafka.scenario;

import org.awaitility.Awaitility;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Stage of the scenario DSL where we await an event of a given type, optionally narrow by deduplication id
 * or predicate, then verify it. Mimics Modulith's {@code andWaitForEventOfType(...).matching(...).toArriveAndVerify(...)}.
 */
public final class AwaitingEvent<T> {

    private final Scenario scenario;
    private final String deduplicationId;
    private final Class<T> type;
    private Predicate<T> predicate = t -> true;

    AwaitingEvent(Scenario scenario, String deduplicationId, Class<T> type) {
        this.scenario = scenario;
        this.deduplicationId = deduplicationId;
        this.type = type;
    }

    private AwaitingEvent(Scenario scenario, String deduplicationId, Class<T> type, Predicate<T> predicate) {
        this.scenario = scenario;
        this.deduplicationId = deduplicationId;
        this.type = type;
        this.predicate = predicate;
    }

    /**
     * Restrict to events with this deduplication id (e.g. the one used in the preceding publish).
     */
    public AwaitingEvent<T> withDeduplicationId(String id) {
        return new AwaitingEvent<>(scenario, id, type, predicate);
    }

    /**
     * Restrict to events matching the given predicate.
     */
    public AwaitingEvent<T> matching(Predicate<T> p) {
        return new AwaitingEvent<>(scenario, deduplicationId, type, p);
    }

    /**
     * Wait (with the scenario timeout) until a matching event arrives, then pass it to the verifier.
     */
    public void toArriveAndVerify(Consumer<T> verifier) {
        Awaitility.await()
                .atMost(scenario.timeout())
                .untilAsserted(() -> {
                    List<T> candidates = deduplicationId == null
                            ? scenario.store().find(type)
                            : scenario.store().findByDeduplicationId(type, deduplicationId);
                    T match = candidates.stream()
                            .filter(predicate)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "No " + type.getSimpleName() + " event matching predicate arrived within timeout"));
                    verifier.accept(match);
                });
    }
}
