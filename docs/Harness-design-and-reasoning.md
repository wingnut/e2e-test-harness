# Harness design: naming, states, simplification, testing

A short note for **developers of the harness** (not end users) to reason about the API and decide on simplification and tests.

---

## 1. State machine view (what we actually have)

We use three staged types after **Scenario**:

| State (type)       | Meaning                          | You can do next |
|--------------------|----------------------------------|------------------|
| **Scenario**       | Ready: no event sent yet         | stimulate, stimulateParallel, withTimeout, publish, andWaitForEventOfType, andWaitForStateChange |
| **Triggered**      | Just triggered (e.g. published; have dedup id) | andWaitForEventOfType, andWaitForStateChange |
| **AwaitingEvent**  | Configuring “which event”        | withDeduplicationId, matching, toArriveAndVerify (void) |
| **ObservedState**  | Have state, need to verify       | andVerify (void) |

Transitions:

- `stimulate` / `stimulateParallel` / `withTimeout` → stay in **Scenario**
- `publish` → **Triggered**
- `andWaitForEventOfType` (from Scenario or Triggered) → **AwaitingEvent**
- `andWaitForStateChange` → **ObservedState**
- `toArriveAndVerify` / `andVerify` → **end** (void)

That’s the full grammar. Use this when you ask “where am I?” or “what can I return from this method?”.

---

## 2. Naming (current)

- **Scenario** – entry point; “the test scenario.”
- **Triggered** – something was triggered (publish or stimulus); we’re now awaiting an outcome (event or state).
- **AwaitingEvent** – we’re in the “await this event” stage; optional narrow (withDeduplicationId, matching), then toArriveAndVerify.
- **ObservedState** – we observed state (e.g. from polling); now verify it with andVerify.

Internal types (in `example.kafka.scenario.internal`, not part of the public API): **ScenarioEventStore**, **ScenarioKafkaConsumer**.

JUnit integration lives in `example.kafka.scenario.junit`: **KafkaScenarioExtension**, **KafkaScenarioTest**. The root `scenario` package stays focused on the DSL (Scenario, Triggered, AwaitingEvent, ObservedState).

---

## 3. Simplification options

### Option A: Inline state-change verification (remove ObservedState)

Today: `andWaitForStateChange(supplier, until).andVerify(consumer)`.

Alternative: one method that takes the verifier and returns void:

- `andWaitForStateChange(Supplier<T> supplier, Predicate<T> until, Consumer<T> verifier)`
- Overload: `andWaitForStateChange(Supplier<T> supplier, Consumer<T> verifier)` for single-shot.

Then you don’t need **ObservedState** at all. The “state change” path has a single terminal method. Slightly less fluent (three arguments instead of two chained calls), but one fewer type and one fewer state to reason about.

### Option B: Keep the current split

The split Scenario → Triggered → AwaitingEvent / ObservedState already matches “trigger → then await event/state → verify.” Simplification might not be worth it if the main pain is reasoning, not line count. In that case, the state machine diagram (above) and a few targeted tests (below) help more.

---

## 4. Do you need tests for the API code?

**You don’t *need* them for correctness** if the example/integration tests (KafkaScenarioExampleTest) already run the full flows. Those tests *are* tests of the API: if the DSL is wrong, they fail.

**You *do* benefit from tests for the API when:**

- You find it hard to reason about “what happens when I call X?”
- You want to change the API without breaking subtle behaviour.
- You want executable documentation of edge cases (e.g. dedup id null vs set, no event vs one event).

**Practical approach:**

1. **Keep the example tests as the main spec** – they define the intended usage and the main paths.
2. **Add a small number of focused tests only where reasoning is hard**, for example:
   - **AwaitingEvent:** “when the store has one matching event, toArriveAndVerify calls the verifier with it”; “when the store has no matching event, toArriveAndVerify eventually throws (timeout).” That can be done with a **real Scenario + store** but no Kafka: construct Scenario with a mock or in-memory producer and a real `ScenarioEventStore` (from the internal package); seed the store in the test; then call the builder and assert. That tests “event selection + verifier” in isolation.
   - **State change:** “when the supplier returns a value that satisfies the predicate, andVerify receives it”; “when the supplier never satisfies the predicate, we get a timeout.” Again, no Kafka needed.
3. **Don’t unit-test every transition** – e.g. “publish returns Triggered” is trivial. Test the **terminal behaviour** (toArriveAndVerify, andWaitForStateChange + andVerify) and the **store/timeout wiring** (e.g. “events are found by type and dedup id”), which are the parts that are easy to get wrong and hard to reason about.

So: **you don’t need tests for every class**, but **a few tests around “wait for event” and “wait for state change”** (with a seeded store or a controlled supplier) make the API easier to reason about and safer to change. The state machine diagram makes the “where am I?” and “what can I do next?” explicit without writing more test code.

---

## 5. One-page “reasoning cheat sheet”

- **States:** Scenario → (optional) Triggered → AwaitingEvent or ObservedState → void.
- **Return type** = “what can happen next.” Same type = same state; new type = next state.
- **Context types** only hold “what the next step needs” and delegate to Scenario/store/timeout.
- **Terminal methods** (toArriveAndVerify, andVerify) do the real work; the rest is wiring.
- **Simplification:** Optionally remove ObservedState by inlining the verifier into andWaitForStateChange.
- **Tests:** Rely on example tests for full flows; add a few tests for “wait for event” and “wait for state” terminal behaviour if you want to reason about and change the API with confidence.
