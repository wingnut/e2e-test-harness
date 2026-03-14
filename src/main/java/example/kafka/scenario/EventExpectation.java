package example.kafka.scenario;

import java.util.List;
import java.util.function.Predicate;

public class EventExpectation<T> {

    private final ScenarioEventStore store;
    private final Class<T> type;
    private String deduplicationId;
    private int expectedCount = 1;
    private Predicate<T> predicate = t -> true;

    public EventExpectation(ScenarioEventStore store, Class<T> type) {
        this.store = store;
        this.type = type;
    }

    public EventExpectation<T> withDeduplicationId(String id) {
        this.deduplicationId = id;
        return this;
    }

    public EventExpectation<T> matching(Predicate<T> p) {
        this.predicate = p;
        return this;
    }

    public EventExpectation<T> times(int n) {
        this.expectedCount = n;
        return this;
    }

    public void verify() {
        List<T> events = (deduplicationId == null)
                ? store.find(type)
                : store.findByDeduplicationId(type, deduplicationId);

        long matches = events.stream().filter(predicate).count();
        if (matches < expectedCount) {
            throw new AssertionError(
                    "Expected " + expectedCount + " " + type.getSimpleName()
                            + " events but got " + matches
            );
        }
    }
}

