package example.kafka.scenario;

import java.util.function.Consumer;

/**
 * Result of {@link PublishedScenario#andWaitForStateChange(java.util.function.Supplier)}
 * or {@link PublishedScenario#andWaitForStateChange(java.util.function.Supplier, java.util.function.Predicate)}.
 * Holds the state (e.g. query result) once it is available; use {@link #andVerify(java.util.function.Consumer)}
 * to assert on it.
 */
public final class StateChangeResult<T> {

    private final T result;

    StateChangeResult(T result) {
        this.result = result;
    }

    /**
     * Verify the state (e.g. assert on the rows returned by the query).
     */
    public void andVerify(Consumer<T> verifier) {
        verifier.accept(result);
    }
}
