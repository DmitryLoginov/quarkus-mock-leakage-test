# Quarkus Mock Leakage Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 21 / Quarkus 3.40.1.1 laboratory that compares isolated mocks with leaked Mockito stubbing and supplies both a green default run and an opt-in order-dependent failure.

**Architecture:** A two-bean CDI application is exercised directly by `@QuarkusTest`. Focused test classes characterize Quarkus `@InjectMock`, plain Mockito `@Mock`, long-lived mocks explicitly installed into CDI, and corrected lifecycles; Maven profiles keep the diagnostic matrix green by default and expose the intentionally red reproduction on demand.

**Tech Stack:** Java 21, Maven, Quarkus 3.40.1.1 (`quarkus-arc`, `quarkus-junit`, `quarkus-junit-mockito`), JUnit 5, Mockito

**Spec:** `docs/superpowers/specs/2026-11-08-quarkus-mock-leakage-lab-design.md`

## Global Constraints

- Pin `io.quarkus.platform:quarkus-bom` and the Quarkus Maven plugin to `3.40.1.1`.
- Compile with Java release 21.
- Keep one Maven module and one minimal CDI application; do not add HTTP, persistence, external services, or `io.quarkus.test.Mock`.
- Tests that depend on method or class order must declare it explicitly; do not use sleeps.
- Default `mvn test` must pass; `mvn test -Pfailure-demo` must produce the intentional order-dependent failure.
- A test named `victim_expects_unconfigured_mock` must pass when selected alone and fail after the preceding leak setup.
- Conclusions must distinguish observed Quarkus behavior from lifecycle misuse.

## Review Focus

- A clean checkout on JDK 21 must resolve Quarkus `3.40.1.1` and boot Arc; Task 1 pins the BOM/plugin and runs `GreetingServiceTest`.
- `@InjectMock` stubbing must not silently persist under default `PER_METHOD`; Task 2's ordered control asserts the second method observes no earlier stubbing and logs mock identities.
- A plain Mockito `@Mock` must not become a CDI replacement accidentally; Task 2 asserts the field is stubbed while `GreetingService` still returns `real greeting`.
- The failure reproduction must be order-dependent rather than always red; Task 3 runs the victim method alone and then the whole failure class.
- Maven selection must never include the intentional failure in the normal build; Task 3 and Task 4 both run default and profile-specific commands.

---

## File Map

- `pom.xml` — Quarkus platform, Java/compiler settings, test dependencies, Surefire defaults, and the `failure-demo` profile.
- `src/main/java/dev/ldv/mockleak/GreetingSource.java` — real `@ApplicationScoped` collaborator returning `real greeting`.
- `src/main/java/dev/ldv/mockleak/GreetingService.java` — `@ApplicationScoped` consumer delegating `greet()` to `GreetingSource.greeting()`.
- `src/test/resources/junit-platform.properties` — deterministic class-name ordering for the cross-class isolation control.
- `src/test/java/dev/ldv/mockleak/MockDiagnostics.java` — package-private console diagnostics for identity, mock status, stubbing count, and observed value.
- `src/test/java/dev/ldv/mockleak/GreetingServiceTest.java` — initial real-bean smoke test.
- `src/test/java/dev/ldv/mockleak/AInjectMockIsolationTest.java` — default `PER_METHOD` `@InjectMock` control.
- `src/test/java/dev/ldv/mockleak/BRealBeanAfterInjectMockTest.java` — verifies the following class receives the real CDI bean.
- `src/test/java/dev/ldv/mockleak/PlainMockitoMockControlTest.java` — proves Mockito `@Mock` is not installed into CDI automatically.
- `src/test/java/dev/ldv/mockleak/InjectMockPerClassProbeTest.java` — characterizes `@InjectMock` under `PER_CLASS` without presupposing a Quarkus defect.
- `src/test/java/dev/ldv/mockleak/InstalledMockitoMockLeakObservationTest.java` — green test that positively observes persisted stubbing.
- `src/test/java/dev/ldv/mockleak/InstalledMockitoMockFixedTest.java` — matching isolated lifecycle.
- `src/test/java/dev/ldv/mockleak/InstalledMockitoMockFailureDemoTest.java` — opt-in victim assertion that passes alone and fails after leak setup.
- `README.md` — commands, expected outcomes, interpretation, and workplace investigation checklist.

### Task 1: Bootstrap the CDI application

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/dev/ldv/mockleak/GreetingSource.java`
- Create: `src/main/java/dev/ldv/mockleak/GreetingService.java`
- Create: `src/test/java/dev/ldv/mockleak/GreetingServiceTest.java`

**Interfaces:**
- Consumes: none
- Produces: `GreetingSource#greeting(): String` returning `real greeting`; `GreetingService(GreetingSource)`; `GreetingService#greet(): String`

- [ ] **Step 1: Write the real-bean smoke test**

Create `GreetingServiceTest`, annotate it with `@QuarkusTest`, inject `GreetingService`, and add `real_source_is_used_without_a_mock()` asserting `greetingService.greet()` equals `real greeting`.

- [ ] **Step 2: Run the smoke test and verify the empty skeleton fails**

Run: `mvn -Dtest=GreetingServiceTest test`

Expected: FAIL during compilation because Quarkus/JUnit and application classes are not configured.

- [ ] **Step 3: Configure the Maven project**

Update `pom.xml` with the Quarkus BOM/plugin version `3.40.1.1`, Java release 21, `quarkus-arc`, test-scoped `quarkus-junit`, test-scoped `quarkus-junit-mockito`, test-scoped `mockito-junit-jupiter`, and a current Surefire plugin compatible with JUnit 5. Preserve artifact coordinates `dev.ldv:quarkus-mock-leakage-test:1.0-SNAPSHOT`.

- [ ] **Step 4: Implement the two CDI beans**

Create `GreetingSource` as `@ApplicationScoped` with `public String greeting()` returning `real greeting`. Create `GreetingService` as `@ApplicationScoped` with constructor injection and `public String greet()` delegating to the source.

- [ ] **Step 5: Run the smoke test and full default suite**

Run: `mvn -Dtest=GreetingServiceTest test`

Expected: PASS, 1 test.

Run: `mvn test`

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit the bootstrap**

```powershell
git add -- pom.xml src/main/java src/test/java/dev/ldv/mockleak/GreetingServiceTest.java
git commit -m "feat: bootstrap Quarkus mock leakage lab"
```

### Task 2: Add isolation controls and diagnostics

**Files:**
- Create: `src/test/resources/junit-platform.properties`
- Create: `src/test/java/dev/ldv/mockleak/MockDiagnostics.java`
- Create: `src/test/java/dev/ldv/mockleak/AInjectMockIsolationTest.java`
- Create: `src/test/java/dev/ldv/mockleak/BRealBeanAfterInjectMockTest.java`
- Create: `src/test/java/dev/ldv/mockleak/PlainMockitoMockControlTest.java`

**Interfaces:**
- Consumes: `GreetingSource#greeting()`, `GreetingService#greet()` from Task 1
- Produces: `MockDiagnostics#print(String scenario, Object candidate, String observed): void`; documented controls for `@InjectMock` and `@Mock`

- [ ] **Step 1: Write the `@InjectMock` per-method control**

In `AInjectMockIsolationTest`, use `@QuarkusTest`, explicit ordered methods, injected `GreetingService`, and `@InjectMock GreetingSource`. `first_method_configures_stubbing()` stubs `greeting()` to `inject-mock greeting` and asserts that value through the service. `second_method_has_no_previous_stubbing()` asserts `greetingService.greet()` is null. Both call `MockDiagnostics.print(...)`.

- [ ] **Step 2: Write the following-class real-bean control**

In `BRealBeanAfterInjectMockTest`, inject `GreetingService` without declaring a mock and assert `real greeting`. Set `junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$ClassName` in `junit-platform.properties` so the `A...` and `B...` names express the cross-class probe order.

- [ ] **Step 3: Write the plain Mockito `@Mock` control**

In `PlainMockitoMockControlTest`, combine `@QuarkusTest` with `@ExtendWith(MockitoExtension.class)`, declare `@Mock GreetingSource plainMock`, and inject `GreetingService`. Stub the field to `plain Mockito greeting`; assert the field returns that value and the CDI service still returns `real greeting`.

- [ ] **Step 4: Run the new tests and verify diagnostics are missing**

Run: `mvn "-Dtest=AInjectMockIsolationTest,BRealBeanAfterInjectMockTest,PlainMockitoMockControlTest" test`

Expected: FAIL during test compilation because `MockDiagnostics` is not yet implemented.

- [ ] **Step 5: Implement `MockDiagnostics`**

Add package-private `static void print(String scenario, Object candidate, String observed)`. Use `Mockito.mockingDetails(candidate)` and print the scenario, `System.identityHashCode(candidate)`, `isMock()`, `getStubbings().size()`, and observed value on one line.

- [ ] **Step 6: Run controls and the full suite**

Run: `mvn "-Dtest=AInjectMockIsolationTest,BRealBeanAfterInjectMockTest,PlainMockitoMockControlTest" test`

Expected: PASS, with different mock identities across the `AInjectMockIsolationTest` methods, no stubbing in the second method, `real greeting` in both non-replaced CDI controls.

Run: `mvn test`

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit the controls**

```powershell
git add -- src/test/java/dev/ldv/mockleak/MockDiagnostics.java src/test/java/dev/ldv/mockleak/AInjectMockIsolationTest.java src/test/java/dev/ldv/mockleak/BRealBeanAfterInjectMockTest.java src/test/java/dev/ldv/mockleak/PlainMockitoMockControlTest.java src/test/resources/junit-platform.properties
git commit -m "test: establish isolated mock controls"
```

### Task 3: Reproduce persisted stubbing and add corrected variants

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/dev/ldv/mockleak/InjectMockPerClassProbeTest.java`
- Create: `src/test/java/dev/ldv/mockleak/InstalledMockitoMockLeakObservationTest.java`
- Create: `src/test/java/dev/ldv/mockleak/InstalledMockitoMockFixedTest.java`
- Create: `src/test/java/dev/ldv/mockleak/InstalledMockitoMockFailureDemoTest.java`

**Interfaces:**
- Consumes: application interfaces from Task 1 and `MockDiagnostics#print(...)` from Task 2
- Produces: default-green lifecycle characterization; Maven profile `failure-demo`; order-dependent victim method `victim_expects_unconfigured_mock()`

- [ ] **Step 1: Write the `@InjectMock` `PER_CLASS` characterization**

Create an ordered `@QuarkusTest` with `@TestInstance(PER_CLASS)`, injected service, and `@InjectMock GreetingSource`. The first method stubs `per-class greeting`; the second records identity, stubbing count, and observed result. Initially assert that the second method still returns `per-class greeting`; run the probe to verify whether Quarkus 3.40.1.1 preserves it. If the assertion fails because Quarkus resets it, change only the second assertion to the actually observed null result and name/output it as isolation evidence.

- [ ] **Step 2: Run the `PER_CLASS` probe**

Run: `mvn -Dtest=InjectMockPerClassProbeTest test`

Expected: PASS after its assertion documents the actual 3.40.1.1 behavior; output must show whether identity/stubbing persisted.

- [ ] **Step 3: Write the deterministic green leakage observation**

Create an ordered `@QuarkusTest` with `@TestInstance(PER_CLASS)`, `@Mock GreetingSource`, injected `GreetingService`, and an `AutoCloseable` from `MockitoAnnotations.openMocks(this)` created in `@BeforeAll` and closed in `@AfterAll`. Install the mock once with `QuarkusMock.installMockForType(...)`. The first method stubs `leaked greeting`; the second asserts the service still returns `leaked greeting` and diagnostics report one stubbing on the same mock.

- [ ] **Step 4: Write the corrected lifecycle test**

Create an ordered default-`PER_METHOD` `@QuarkusTest`. Initialize `@Mock GreetingSource`, install it in CDI, and close Mockito annotations per method with `@BeforeEach`/`@AfterEach`. The first method stubs and sees `isolated greeting`; the second asserts the service result is null and diagnostics report zero stubbings. If Quarkus callback ordering makes per-method `installMockForType` unsuitable, retain `PER_CLASS` and call `Mockito.reset(source)` in `@BeforeEach`; preserve the same assertions and explain the fallback in test output.

- [ ] **Step 5: Run the green leak/fix comparison**

Run: `mvn "-Dtest=InstalledMockitoMockLeakObservationTest,InstalledMockitoMockFixedTest" test`

Expected: PASS; the leak observation reports the same mock with one surviving stubbing, while the fixed test reports no surviving stubbing in its second method.

- [ ] **Step 6: Write the opt-in failure demo**

Create `InstalledMockitoMockFailureDemoTest` with the same once-per-class setup as the green observation. `@Order(1) leak_setup_configures_stubbing()` returns `leaked greeting`. `@Order(2) victim_expects_unconfigured_mock()` asserts `greetingService.greet()` is null. Do not weaken the victim assertion.

- [ ] **Step 7: Configure default exclusion and the `failure-demo` profile**

In the main Surefire configuration, use properties whose defaults include `**/*Test.java` and exclude `**/*FailureDemoTest.java`. Add profile `failure-demo` that includes `**/*FailureDemoTest.java` and changes the exclusion to a nonmatching pattern. Keep the Java logging manager system property required by Quarkus tests.

- [ ] **Step 8: Verify the victim passes by itself**

Run: `mvn -Pfailure-demo "-Dtest=InstalledMockitoMockFailureDemoTest#victim_expects_unconfigured_mock" test`

Expected: PASS because the preceding stubbing method did not run.

- [ ] **Step 9: Verify the ordered failure and default-green contract**

Run: `mvn -Pfailure-demo test`

Expected: FAIL only in `victim_expects_unconfigured_mock`, expected null but observed `leaked greeting`.

Run: `mvn test`

Expected: BUILD SUCCESS and the failure demo is absent from the executed tests.

- [ ] **Step 10: Commit the experiment matrix**

```powershell
git add -- pom.xml src/test/java/dev/ldv/mockleak/InjectMockPerClassProbeTest.java src/test/java/dev/ldv/mockleak/InstalledMockitoMockLeakObservationTest.java src/test/java/dev/ldv/mockleak/InstalledMockitoMockFixedTest.java src/test/java/dev/ldv/mockleak/InstalledMockitoMockFailureDemoTest.java
git commit -m "test: demonstrate mock stubbing leakage"
```

### Task 4: Document and verify the laboratory

**Files:**
- Create: `README.md`
- Modify if evidence requires: `src/test/java/dev/ldv/mockleak/InjectMockPerClassProbeTest.java`

**Interfaces:**
- Consumes: all Maven commands and observed outcomes from Tasks 1–3
- Produces: user-facing experiment guide and evidence-based conclusions

- [ ] **Step 1: Write the README**

Document prerequisites, project structure, the test matrix, exact PowerShell-safe commands, expected outputs, and these distinctions: `@InjectMock` replaces a CDI bean for the owning Quarkus test; plain `@Mock` does not; a long-lived Mockito mock explicitly installed into CDI can retain stubbing; `clearInvocations` does not clear stubbing but `reset` does. Link the official Quarkus testing guide.

- [ ] **Step 2: Add the workplace investigation checklist**

Include checks for `@TestInstance(PER_CLASS)`, static mocks/fields, inherited test bases, `MockitoAnnotations.openMocks`/`MockitoSession` closure, manual `QuarkusMock` installation, parallel execution, and test profiles/resources. State the actual `InjectMockPerClassProbeTest` result without generalizing beyond 3.40.1.1.

- [ ] **Step 3: Verify all documented green commands**

Run: `mvn clean test`

Expected: BUILD SUCCESS.

Run: `mvn -Pfailure-demo "-Dtest=InstalledMockitoMockFailureDemoTest#victim_expects_unconfigured_mock" test`

Expected: BUILD SUCCESS.

- [ ] **Step 4: Verify the documented red command**

Run: `mvn -Pfailure-demo test`

Expected: BUILD FAILURE only because `victim_expects_unconfigured_mock` observes `leaked greeting`.

- [ ] **Step 5: Perform repository checks**

Run: `git diff --check`

Expected: no output.

Run: `git status --short`

Expected: only the intended README or evidence-driven test adjustment is uncommitted.

- [ ] **Step 6: Commit the guide**

```powershell
git add -- README.md src/test/java/dev/ldv/mockleak/InjectMockPerClassProbeTest.java
git commit -m "docs: explain mock leakage experiments"
```

- [ ] **Step 7: Record final evidence for handoff**

Capture the passing test count from `mvn clean test`, the isolated victim command, and the exact failing assertion from `mvn -Pfailure-demo test`. Do not claim success from an earlier run.
