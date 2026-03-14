package example.kafka.scenario;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Scenario DSL entry point, similar to Spring Modulith's Scenario API
 * but backed by Kafka as the event bus.
 */
public class Scenario {

    private final Producer<String, String> producer;
    private final ScenarioEventStore store;
    private final String topic;
    private final List<EventExpectation<?>> expectations = new CopyOnWriteArrayList<>();
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
     * Register an expectation for events of the given type.
     */
    public <T> EventExpectation<T> expect(Class<T> type) {
        EventExpectation<T> exp = new EventExpectation<>(store, type);
        expectations.add(exp);
        return exp;
    }

    /**
     * Sequential verification of all expectations with Awaitility.
     */
    public void verify() {
        Awaitility.await()
                .atMost(timeout)
                .untilAsserted(() -> expectations.forEach(EventExpectation::verify));
    }

    /**
     * Parallel verification of all expectations.
     */
    public void verifyParallel() {
        ExecutorService executor = Executors.newFixedThreadPool(expectations.size());
        try {
            for (EventExpectation<?> expectation : expectations) {
                Future<?> f = executor.submit(expectation::verify);
                f.get();
            }
        } catch (Exception ex) {
            throw new AssertionError("Parallel verification failed", ex);
        } finally {
            executor.shutdownNow();
        }
    }
}

