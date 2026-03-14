# Fluent APIs and DSL-style programming in Java

A short tutorial on the style used in this harness: chaining methods, staged return types, and “grammar” in code.

---

## 1. What problem does it solve?

**Ordinary API:** You call one operation, then another, and you have to remember the valid order yourself.

```java
scenario.publish(id, event);
scenario.waitForEvent(String.class, id);  // Did we publish first? Compiler doesn't care.
```

**Fluent / DSL-style API:** The way you write the code reflects the intended flow, and the type system can restrict invalid sequences.

```java
scenario
    .publish(id, event)
    .andWaitForEventOfType(String.class)
    .matching(s -> s.contains("ok"))
    .toArriveAndVerify(e -> assertTrue(...));
```

Here, “wait for event” only appears *after* “publish,” and the compiler only offers methods that make sense at each step.

---

## 2. Two main techniques

### 2.1 Return `this` to allow chaining (fluent interface)

If a method returns the same object, the next method call is on that object, so you can chain without repeating the variable name.

```java
public Scenario stimulate(Runnable r) {
    r.run();
    return this;   // same Scenario, so caller can keep chaining
}
```

So you can write: `scenario.stimulate(...).withTimeout(...).publish(...)`.

**When to use:** When the same “stage” of the flow can do several things in any order (e.g. configure then act). Returning `this` keeps you in that stage.

### 2.2 Return a *different* type to move to the next stage (staged DSL)

If a method returns a *new* type, that type can expose only the operations that are valid *after* that step.

- After `publish()`, you get a `Triggered`: it only offers “wait for event” and “wait for state change,” not “publish again” (unless you add it).
- After `andWaitForEventOfType()`, you get an `AwaitingEvent`: it only offers “withDeduplicationId,” “matching,” and “toArriveAndVerify.”

So the **return type** encodes “where you are” in the flow. Invalid next steps simply don’t exist on that type, so the API “guides” the caller and avoids nonsense sequences.

**When to use:** When the flow has a clear order (e.g. “publish → then wait”) and you want to enforce it at compile time.

---

## 3. Small “context” objects

In a DSL, you often introduce small types that don’t do much logic; they mainly **hold context** for the next step and delegate to shared infrastructure.

- `Triggered` holds: “which scenario” and “which deduplication id we just used.” The real work (store, timeout, Awaitility) lives in `Scenario` or shared helpers; `Triggered` just passes that context into `AwaitingEvent` or state-change logic.
- `AwaitingEvent` holds: event type, optional dedup id, predicate. It then calls the scenario’s store and timeout when you call `toArriveAndVerify`.

So “wiring up” a DSL is largely: **split the journey into stages, give each stage a type, and have each type carry the context needed for the next verb.**

---

## 4. Void at the end

Chains often end with a method that returns `void`: e.g. `toArriveAndVerify(...)` or `andVerify(...)`. That’s the “do the thing” or “assert” step. There is no next step in the sentence, so the type doesn’t need to offer one.

---

## 5. Summary table

| Technique              | Purpose |
|-------------------------|--------|
| Return `this`           | Stay in the same “stage”; allow chaining (e.g. `stimulate().withTimeout()`) |
| Return new type         | Move to next stage; only expose valid next operations |
| Small context objects   | Hold “where we are” and “what we know”; delegate to shared logic |
| Void at the end         | End the sentence (execute/assert) |

---

## 6. Further reading

- **Baeldung – Fluent interface vs Builder:**  
  [https://www.baeldung.com/java-fluent-interface-vs-builder-pattern](https://www.baeldung.com/java-fluent-interface-vs-builder-pattern)  
  Clarifies fluent interface vs classic builder (e.g. object construction).

- **Baeldung – Builder pattern:**  
  [https://www.baeldung.com/java-builder-pattern](https://www.baeldung.com/java-builder-pattern)  
  Classic builder with “return this” and `build()`.

- **Designing Fluent APIs with generics (Java):**  
  Search for “designing fluent APIs with generics Java” for type-safe builders and self-referential generics when you have inheritance in the chain.

- **Martin Fowler – FluentInterface:**  
  [https://martinfowler.com/bliki/FluentInterface.html](https://martinfowler.com/bliki/FluentInterface.html)  
  Original idea and trade-offs (readability vs debugging, discoverability).

---

## 7. In this project

- **Scenario:** entry point; returns `this` for `stimulate`, `withTimeout`, `stimulateParallel`; returns `Triggered` for `publish`; also exposes `andWaitForEventOfType` / `andWaitForStateChange` (no publish) so you can do “stimulate then wait.”
- **Triggered:** something was triggered (e.g. publish); only “andWaitForEventOfType” and “andWaitForStateChange”; delegates to scenario’s store and timeout.
- **AwaitingEvent:** “awaiting event” stage; only “withDeduplicationId,” “matching,” “toArriveAndVerify”; ends with void.
- **ObservedState:** observed state (e.g. from polling); only “andVerify”; ends with void.

That’s the same style: **fluent chaining + staged return types + small context objects + void at the end.**
