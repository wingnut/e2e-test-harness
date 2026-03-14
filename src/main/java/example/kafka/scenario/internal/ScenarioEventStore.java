package example.kafka.scenario.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Internal: per-scenario event capture for the DSL. Not part of the public API.
 */
public final class ScenarioEventStore {

    private final List<ScenarioEvent> events = new CopyOnWriteArrayList<>();

    void record(String topic, String deduplicationId, Object payload) {
        events.add(new ScenarioEvent(topic, deduplicationId, payload));
    }

    /** Used by {@link example.kafka.scenario.AwaitingEvent} to resolve matching events. */
    public <T> List<T> find(Class<T> type) {
        return events.stream()
                .map(ScenarioEvent::payload)
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    /** Used by {@link example.kafka.scenario.AwaitingEvent} to resolve events by deduplication id. */
    public <T> List<T> findByDeduplicationId(Class<T> type, String deduplicationId) {
        return events.stream()
                .filter(e -> deduplicationId.equals(e.deduplicationId()))
                .map(ScenarioEvent::payload)
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    record ScenarioEvent(String topic, String deduplicationId, Object payload) {}
}
