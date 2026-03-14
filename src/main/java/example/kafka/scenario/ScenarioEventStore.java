package example.kafka.scenario;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

class ScenarioEventStore {

    private final List<ScenarioEvent> events = new CopyOnWriteArrayList<>();

    void record(String topic, String deduplicationId, Object payload) {
        events.add(new ScenarioEvent(topic, deduplicationId, payload));
    }

    <T> List<T> find(Class<T> type) {
        return events.stream()
                .map(ScenarioEvent::payload)
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    <T> List<T> findByDeduplicationId(Class<T> type, String deduplicationId) {
        return events.stream()
                .filter(e -> deduplicationId.equals(e.deduplicationId()))
                .map(ScenarioEvent::payload)
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    record ScenarioEvent(String topic, String deduplicationId, Object payload) {}
}

