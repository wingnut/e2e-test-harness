package example.kafka.scenario;

import java.util.function.Consumer;

/**
 * Stage of the scenario DSL holding observed state (e.g. query result) after
 * {@link Triggered#andWaitForStateChange(java.util.function.Supplier)}
 * or {@link Triggered#andWaitForStateChange(java.util.function.Supplier, java.util.function.Predicate)}.
 * Use {@link #andVerify(java.util.function.Consumer)} to assert on it.
 */
public final class ObservedState<T> {

    private final T result;

    ObservedState(T result) {
        this.result = result;
    }

    /**
     * Verify the state (e.g. assert on the rows returned by the query).
     */
    public void andVerify(Consumer<T> verifier) {
        verifier.accept(result);
    }
}
