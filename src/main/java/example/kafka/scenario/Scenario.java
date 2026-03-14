package example.kafka.scenario;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Scenario DSL entry point, Modulith-style: publish then andWaitForEventOfType / andWaitForStateChange.
 * Backed by Kafka as the event bus.
 */
public class Scenario {

    private final Producer<String, String> producer;
    private final ScenarioEventStore store;
    private final String topic;
    private Duration timeout = Duration.ofSeconds(30);

    Scenario(Producer<String, String> producer,
             ScenarioEventStore store,
             String topic) {
        this.producer = producer;
        this.store = store;
        this.topic = topic;
    }

    ScenarioEventStore store() {
        return store;
    }

    Duration timeout() {
        return timeout;
    }

    /**
     * Expose the underlying Kafka topic name for this scenario.
     * Useful for wiring test-local components that should participate
     * in the same event stream (e.g. fake domain services).
     */
    public String topicName() {
        return topic;
    }

    /**
     * Override default verification timeout for this scenario.
     */
    public Scenario withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sequential stimulus.
     */
    public Scenario stimulate(Runnable stimulus) {
        stimulus.run();
        return this;
    }

    /**
     * Parallel stimulus using a fixed thread pool.
     */
    public Scenario stimulateParallel(Runnable... stimuli) {
        ExecutorService executor = Executors.newFixedThreadPool(stimuli.length);
        try {
            for (Runnable r : stimuli) {
                Future<?> f = executor.submit(r);
                f.get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
        return this;
    }

    /**
     * Publish an event to the scenario topic with the given deduplication id as key.
     * Returns a fluent API for Modulith-style andWaitForStateChange / andWaitForEventOfType.
     */
    public PublishedScenario publish(String deduplicationId, String event) {
        producer.send(new ProducerRecord<>(topic, deduplicationId, event));
        return new PublishedScenario(this, deduplicationId);
    }

    /**
     * Wait for an event of the given type (no prior publish). Use after {@link #stimulate(Runnable)}
     * when the stimulus causes something else to publish (e.g. call an API that emits an event).
     * Events are matched without a deduplication id filter; use {@link EventWaitBuilder#withDeduplicationId(String)}
     * or {@link EventWaitBuilder#matching(Predicate)} to narrow.
     */
    public <T> EventWaitBuilder<T> andWaitForEventOfType(Class<T> type) {
        return new EventWaitBuilder<>(this, null, type);
    }

    /**
     * Wait for a state change (no prior publish). Use after {@link #stimulate(Runnable)} when the
     * stimulus triggers an async update (e.g. a batch job) and you poll until a condition holds.
     */
    public <T> StateChangeResult<T> andWaitForStateChange(Supplier<T> stateSupplier, Predicate<T> until) {
        return new PublishedScenario(this, null).andWaitForStateChange(stateSupplier, until);
    }

    /**
     * Run a state supplier once and verify (no prior publish). Use after {@link #stimulate(Runnable)}
     * when the stimulus returns or produces the state directly.
     */
    public <T> StateChangeResult<T> andWaitForStateChange(Supplier<T> stateChange) {
        return new PublishedScenario(this, null).andWaitForStateChange(stateChange);
    }
}

