# Quarkus Mock Leakage Lab — Design

## Intent

Build a small, controlled Maven project that investigates order-dependent mock behavior in `@QuarkusTest` on Quarkus `3.40.1.1`. The laboratory must let a developer compare a deliberately leaky mock lifecycle with an isolated lifecycle in the same checkout, and must distinguish actual Quarkus behavior from misuse of Mockito lifecycle APIs.

The motivating symptom is a suite in which tests pass individually but some fail in a full run because stubbing configured for one test appears to affect another test.

## Goals

- Pin Quarkus to `3.40.1.1` and Java 21.
- Use one minimal CDI application shared by all experiments.
- Cover both `io.quarkus.test.InjectMock` and `org.mockito.Mock`.
- Demonstrate, side by side:
  - the supported, isolated behavior of `@InjectMock` with JUnit's default per-method test lifecycle;
  - that a plain Mockito `@Mock` is not installed into CDI automatically;
  - at least one deterministic way for configured stubbing to survive into a later test;
  - the corresponding corrected lifecycle.
- Make mock identity, installed CDI behavior, and active Mockito stubbings visible in test output.
- Keep the normal build green while also providing an explicit command that reproduces the familiar red, order-dependent failure.
- Document what each result proves and, equally importantly, what it does not prove about the production test suite.

## Non-goals

- Claim that Quarkus `3.40.1.1` has a framework defect unless a minimal cross-class probe actually demonstrates one.
- Recreate the proprietary application or its complete dependency graph.
- Cover `io.quarkus.test.Mock`, native-image tests, Panache static mocking, REST clients, or external mock servers.
- Turn this into a production service; HTTP endpoints and persistence are unnecessary.
- Make intentionally unsafe parallel tests part of the default reliable build.

## Anti-goals

- A permanently red default `mvn test` run.
- A demonstration whose "leak" is only an unexplained static variable with no relation to Quarkus CDI replacement.
- Depending on unspecified JUnit class ordering or timing alone.
- Presenting Mockito `@Mock` as if Quarkus injects it into application beans automatically.

## Constraints

- Existing repository is an uncommitted Maven/Java 21 skeleton.
- The exact root cause in the work application is unknown, so the project is an evidence-producing lab rather than a predetermined bug reproducer.
- Tests that depend on order must declare that order explicitly.
- Experiments must be runnable on Windows PowerShell with Maven.
- Quarkus starts one application for compatible `@QuarkusTest` classes; the design should expose when a mock is local to a test instance versus installed into the shared CDI application.

## Approaches Considered

### Selected: one Maven module with named test groups and an opt-in failure profile

All application code and experiments live together. Safe controls, observed-leak tests, and corrected tests are available side by side. The normal test run asserts the observed behavior and stays green. A Maven profile enables a victim-style assertion that intentionally expects clean state and therefore turns red when the leak is present.

This best matches the desire to inspect both variants simultaneously and minimizes build structure that could itself isolate Quarkus contexts.

### Rejected: Maven multi-module lab

Separate `baseline`, `leaky`, and `isolated` modules would be visually clear, but each Quarkus module would boot its own application. That removes the shared-context condition under investigation and requires duplicate application code.

### Rejected: separate Git branches

Branches keep each variant clean but prevent side-by-side inspection and a single comparative run. Configuration differences are also harder to audit.

## Application Model

The production side contains two small beans:

- `GreetingSource`, an `@ApplicationScoped` collaborator whose real response is a recognizable constant such as `real greeting`.
- `GreetingService`, an `@ApplicationScoped` consumer that delegates to `GreetingSource` and is the bean tests invoke.

Tests mock `GreetingSource` and observe behavior through `GreetingService`. This ensures the experiment exercises CDI replacement instead of merely calling a mock field directly.

No HTTP layer is needed. Direct CDI injection makes object identity and lifecycle easier to reason about.

## Experiment Matrix

### 1. `@InjectMock` isolation control

An ordered `@QuarkusTest` uses the default JUnit `PER_METHOD` lifecycle. One method stubs the injected mock; a later method does not configure that behavior. The later method verifies that the earlier stubbing is absent. Diagnostic output includes the mock identity and its stubbings.

A separate `@QuarkusTest` without `@InjectMock` verifies that `GreetingService` sees the real `GreetingSource`. This checks the documented class boundary rather than assuming it.

### 2. Plain Mockito `@Mock` control

A `@QuarkusTest` initializes an `org.mockito.Mock` through `MockitoExtension` or an explicitly scoped `MockitoSession`. It stubs that field while also injecting `GreetingService`. The test proves that the field behaves as configured but the CDI service still uses the real bean until the mock is explicitly installed into CDI.

### 3. Deliberately leaky `@InjectMock` lifecycle

An ordered `@QuarkusTest` uses `@TestInstance(PER_CLASS)` together with `@InjectMock`. The first method configures stubbing and the following method observes whether the same mock and stubbing remain. The green diagnostic form asserts the behavior actually observed on `3.40.1.1`.

If Quarkus resets or recreates this mock despite `PER_CLASS`, the result is recorded as an isolated control, not forced into a fake leak. The implementation then retains the test as evidence and relies on experiment 4 for the deterministic leak.

### 4. Deliberately leaky plain `@Mock` installed into CDI

An ordered `@QuarkusTest` with `PER_CLASS` initializes a Mockito `@Mock` once for the class and explicitly installs it for `GreetingSource` using `QuarkusMock`. The first method adds stubbing; the later method demonstrates that the same installed mock still has that stubbing.

This is deterministic and represents a realistic lifecycle error: a long-lived Mockito mock was installed into the Quarkus CDI context, but its stubbing was not reset between test methods.

### 5. Corrected variants

The same behaviors are tested with safe lifecycles:

- prefer JUnit's default `PER_METHOD` lifecycle;
- initialize and install a plain Mockito mock per test when CDI replacement is needed;
- configure all required behavior in `@BeforeEach` or in the test that owns it;
- when `PER_CLASS` is unavoidable, use `Mockito.reset(...)` before the next test (and explain that `clearInvocations(...)` alone does not remove stubbing).

A profile-based Quarkus restart may be shown as a stronger but slower isolation boundary only if the implementation probes reveal a cross-class CDI replacement issue. It is not the default fix for an ordinary Mockito lifecycle problem.

### 6. Optional parallel cross-class probe

An opt-in Maven profile may run two coordinated `@QuarkusTest` classes concurrently against the same bean type. It must use synchronization primitives rather than sleeps. This probe is diagnostic only because parallel mutation of a shared CDI replacement is an unsafe setup. It is omitted if Quarkus/JUnit cannot run the classes concurrently in a stable, comprehensible way.

## Test and Maven Behavior

- `mvn test` runs controls, green leak-observation tests, and corrected variants. It must pass.
- Individual classes can be selected with standard `-Dtest=...` syntax.
- A profile such as `failure-demo` enables an explicitly named victim test. That test performs the natural clean-state assertion after the leaky setup and is expected to fail, recreating the "passes alone, fails in the suite" experience.
- The intentionally failing test is excluded from the default Surefire selection.
- Test names and console messages state whether they are controls, observations, or recommended fixes.

The implementation may adjust the exact Surefire include/exclude mechanism if Maven profile merging makes a clearer command possible, but the default-green/opt-in-red contract must remain.

## Diagnostics

Each relevant test reports:

- test class and method;
- `System.identityHashCode` of the Mockito mock;
- whether `Mockito.mockingDetails(...).isMock()` is true;
- current stubbings and the method/result observed through `GreetingService`.

Diagnostics should be small helper methods, not a logging framework. Assertions remain the source of truth; output only explains failures.

## Documentation

`README.md` will contain:

- prerequisites (JDK 21 and Maven);
- the experiment matrix and exact commands;
- expected green and red outcomes;
- an explanation of `@InjectMock` versus Mockito `@Mock`;
- a concise interpretation guide for applying the findings to the work project;
- likely next checks there: `PER_CLASS`, base classes, static fields, manual `QuarkusMock` installation, Mockito sessions, parallel execution, and test/profile boundaries.

It will cite the official Quarkus testing guide for the documented scope of `@InjectMock` and clearly label any conclusions drawn from local experiments.

## Success Criteria

- The project builds on Java 21 with Quarkus `3.40.1.1`.
- Default `mvn test` is green and visibly exercises both leaky and isolated variants.
- An opt-in command produces a deterministic, explained failure representing leaked stubbing.
- Running the victim test alone passes, while running it after its leak setup through the documented command fails.
- At least one test proves that plain Mockito `@Mock` is not automatically a CDI replacement.
- README conclusions match actual command output; uncertain framework behavior is reported as evidence, not asserted in advance.

## Decisions Left to the Builder

- Exact package and class names.
- Whether diagnostics use `System.out` or JBoss logging.
- The smallest stable Maven profile/include arrangement that satisfies the command contracts.
- Whether the optional parallel probe is sufficiently deterministic to keep.
