# Codex Runtime & App Server Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Harden the Codex Java SDK across the 1.0.x, 2.0.x, and 3.0.x version lines so CLI configuration is truthful, app-server protocol behavior is canonical and race-free, same-session turns are serialized, and real message deltas stream without duplicate output.

**Architecture:** `feature/2.0.x` is the canonical App Server implementation; `feature/3.0.x` receives forward-ports adapted to JDK 21 / Jackson 3; `feature/1.0.x` receives shared CLI fixes only. The App Server path keeps request-scoped WebSocket connections but centralizes protocol constants, serializes writes, adds event/listener compatibility, and introduces a per-session execution coordinator plus a thread-mapping store seam.

**Tech Stack:** Java 8 / 17 / 21, Maven 3.9.16 / 4.0.0-rc-5, Jackson 2.18.9 / 2.22.1 / 3.2.1, Apache Commons Exec 1.6.0, JDK `java.net.http.WebSocket` for 2.0.x/3.0.x, JUnit 5/6.

**Spec:** `docs/superpowers/specs/2026-09-20-codex-runtime-appserver-hardening-design.md`

## Global Constraints

- `feature/1.0.x` remains JDK 8, Maven 3.9.16, Jackson 2.18.9, and must not gain the app-server package.
- `feature/2.0.x` remains JDK 17, Maven 3.9.16, Jackson 2.22.1, and is the canonical App Server protocol line.
- `feature/3.0.x` remains JDK 21, Maven 4.0.0-rc-5, Jackson 3.2.1, and must match 2.0.x App Server semantics.
- Existing public APIs remain source-compatible unless an additive replacement is introduced.
- Current CLI process guarantees must not regress: UTF-8, real non-zero exit codes, stdout/stderr preservation, stdin EOF, argument fidelity, and timeout behavior.
- App Server continues to use request-scoped WebSocket connections; persistent connection multiplexing is out of scope.
- Unknown app-server notifications remain non-fatal unless the server explicitly reports an error.
- All production behavior changes use test-first RED → GREEN → REFACTOR.
- Before each commit, run the smallest owning test set; before branch completion, run the full branch suite and build.

## Review Focus

1. **Probe timeout isolation:** a probe timeout must not mutate the client-wide normal command timeout or race with concurrent normal commands.
2. **JSON mode semantics:** `jsonOutput=false` must affect normal `exec`, while `execAndParse` must still force JSONL for its own call.
3. **WebSocket send sequencing:** a delayed `notifications/initialized` send must prevent the business request from being emitted early, and send failures must surface immediately.
4. **Same-session concurrency:** two concurrent turns with the same `sessionKey` must serialize without leaking coordinator entries; different sessions must remain concurrent.
5. **Streaming de-duplication:** real `item/agentMessage/delta` events must not be followed by duplicate full-message delivery from `item/completed`.

---

## File Map

### Shared CLI files

- `src/main/java/io/github/easy4j/codex/CodexClientConfig.java`
  - Existing configuration model; no new fields required for this phase.
- `src/main/java/io/github/easy4j/codex/CodexClient.java`
  - Honors `jsonOutput`, forces JSON only for parsing APIs, and builds global interactive defaults including `noAltScreen`.
- `src/main/java/io/github/easy4j/codex/cli/CodexCliExecutor.java`
  - Adds an internal timeout-aware execution path so probe can use `localProbeTimeoutSeconds` without mutating shared config.
- `src/test/java/io/github/easy4j/codex/CodexClientTest.java`
  - Regression coverage for JSON mode and interactive defaults.
- `src/test/java/io/github/easy4j/codex/cli/CodexCliExecutorTest.java`
  - Regression coverage for probe timeout isolation.

### App Server 2.0.x canonical files

- `src/main/java/io/github/easy4j/codex/appserver/CodexAppServerProtocol.java`
  - New package-private canonical method/notification names.
- `src/main/java/io/github/easy4j/codex/appserver/CodexWebSocketSender.java`
  - New package-private serialized send coordinator with immediate failure propagation.
- `src/main/java/io/github/easy4j/codex/appserver/CodexAppServerRpc.java`
  - Uses protocol constants and serialized sender; sends `notifications/initialized`.
- `src/main/java/io/github/easy4j/codex/appserver/CodexAppServerTurn.java`
  - Uses protocol constants/sender; supports real agent-message deltas and fallback de-duplication.
- `src/main/java/io/github/easy4j/codex/appserver/CodexAppServerListener.java`
  - New additive listener API with default methods.
- `src/main/java/io/github/easy4j/codex/appserver/AppServerTurnRequest.java`
  - Adds optional listener while retaining current callbacks.
- `src/main/java/io/github/easy4j/codex/appserver/SessionExecutionCoordinator.java`
  - New package-private per-session serial coordinator.
- `src/main/java/io/github/easy4j/codex/appserver/ThreadMappingStore.java`
  - New store abstraction.
- `src/main/java/io/github/easy4j/codex/appserver/ThreadMappingCache.java`
  - Implements the store contract and gains `remove`.
- `src/main/java/io/github/easy4j/codex/appserver/CodexAppServerClient.java`
  - Owns coordinator/store and serializes same-session turns.
- `src/main/java/io/github/easy4j/codex/appserver/AppServerTurnResult.java`
  - Uses actual completion status/reason when available; neutral fallback `completed`.
- `src/test/java/io/github/easy4j/codex/appserver/CodexAppServerTurnTest.java`
  - Protocol, streaming, de-duplication, write-failure, finish-reason tests.
- `src/test/java/io/github/easy4j/codex/appserver/CodexAppServerClientTest.java`
  - Client lifecycle/store/coordinator unit tests.
- `src/test/java/io/github/easy4j/codex/appserver/CodexAppServerClientE2ETest.java`
  - End-to-end handshake, concurrency, streaming tests.
- `src/test/java/io/github/easy4j/codex/appserver/FakeCodexAppServer.java`
  - Scriptable/delay-capable fake server for ordering and concurrency tests.
- `src/test/java/io/github/easy4j/codex/appserver/ThreadMappingCacheTest.java`
  - Store contract and remove behavior.
- `src/test/java/io/github/easy4j/codex/appserver/CodexAppServerRealIntegrationTest.java`
  - Remains opt-in real-wire verification.

### 3.0.x forward-port files

The same App Server file set as 2.0.x, adapted only for Jackson 3 package names and existing JDK 21 syntax/build conventions.

---

## Task 1: Fix CLI Probe Timeout Semantics on feature/1.0.x

**Files:**
- Modify: `src/main/java/io/github/easy4j/codex/cli/CodexCliExecutor.java`
- Test: `src/test/java/io/github/easy4j/codex/cli/CodexCliExecutorTest.java`

**Interfaces:**
- Consumes: `CodexClientConfig#getLocalTimeoutSeconds()`, `CodexClientConfig#getLocalProbeTimeoutSeconds()`
- Produces: package-private/internal `runProcess(String stdin, long timeoutMs, String... args)` or equivalent; public API remains unchanged.

- [x] **Step 1: Write the failing probe-timeout regression test**

Add a test executable/script that sleeps longer than the probe timeout but shorter than the normal timeout, then configure:

```java
config.setLocalExecutable(slowExecutable);
config.setLocalTimeoutSeconds(10);
config.setLocalProbeTimeoutSeconds(1);

CodexCliExecutor executor = new CodexCliExecutor(config);

long started = System.nanoTime();
assertFalse(executor.probe());
long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

assertTrue(elapsedMs < 5_000, "probe must use the probe timeout, not normal command timeout");
```

Also add a second assertion/test showing a normal `execute(...)` call still uses `localTimeoutSeconds`.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
mvn -q -Dtest=CodexCliExecutorTest test
```

Expected: the new probe timeout test fails because `probe()` currently delegates to `execute("--version")` and uses `localTimeoutSeconds`.

- [x] **Step 3: Implement the minimal timeout-aware execution path**

Refactor without changing public API:

```java
public CodexCliResult execute(String... args) {
    return runProcess(null, config.getLocalTimeoutSeconds() * 1000L, args);
}

public CodexCliResult executeWithStdin(String stdin, String... args) {
    return runProcess(stdin, config.getLocalTimeoutSeconds() * 1000L, args);
}

private CodexCliResult executeWithTimeoutSeconds(int timeoutSeconds, String... args) {
    return runProcess(null, timeoutSeconds * 1000L, args);
}

public boolean probe() {
    try {
        return executeWithTimeoutSeconds(config.getLocalProbeTimeoutSeconds(), "--version").isSuccess();
    } catch (Exception ignored) {
        return false;
    }
}
```

Keep the existing Java 8 UTF-8 helper unchanged.

- [x] **Step 4: Re-run focused tests and verify GREEN**

Run:

```bash
mvn -q -Dtest=CodexCliExecutorTest test
```

Expected: all `CodexCliExecutorTest` tests pass.

- [x] **Step 5: Run the full 1.0.x suite**

```bash
mvn -B --no-transfer-progress clean verify
```

- [x] **Step 6: Commit**

```bash
git add src/main/java/io/github/easy4j/codex/cli/CodexCliExecutor.java         src/test/java/io/github/easy4j/codex/cli/CodexCliExecutorTest.java
git commit -m "fix(cli): honor probe timeout independently"
```

---

## Task 2: Fix CLI JSON and Interactive Defaults on feature/1.0.x

**Files:**
- Modify: `src/main/java/io/github/easy4j/codex/CodexClient.java`
- Test: `src/test/java/io/github/easy4j/codex/CodexClientTest.java`

**Interfaces:**
- Consumes: `CodexClientConfig#isJsonOutput()`, `CodexClientConfig#isNoAltScreen()`
- Produces: private `defaultGlobalOptions()`; normal `exec` honors configured JSON mode; `execAndParse` explicitly requests JSON.

- [x] **Step 1: Write a failing test for `jsonOutput=false`**

Use the existing fake/echo CLI arrangement and assert normal `exec` does not include `--json` when:

```java
config.setJsonOutput(false);
CodexClient client = new CodexClient(config);
client.exec("hello");
```

- [x] **Step 2: Write a failing test proving `execAndParse` still forces JSON**

Configure `jsonOutput=false`, invoke `execAndParse`, and assert the underlying argv contains `--json` and valid JSONL is parsed.

- [x] **Step 3: Write a failing test for `noAltScreen=true`**

```java
config.setNoAltScreen(true);
client.startSession("hello");
```

Assert argv contains `--no-alt-screen`.

- [x] **Step 4: Run focused tests and verify RED**

```bash
mvn -q -Dtest=CodexClientTest test
```

- [x] **Step 5: Implement minimal configuration propagation**

Change `defaultOptions`:

```java
private CodexCli.ExecOptions defaultOptions(String prompt) {
    CodexCli.ExecOptions opts = new CodexCli.ExecOptions(prompt)
            .json(config.isJsonOutput());
    // preserve existing defaults
    return opts;
}
```

Change `execAndParse`:

```java
public List<CodexEvent> execAndParse(String prompt) {
    CodexCli.ExecOptions opts = defaultOptions(prompt).json(true);
    CodexCliResult result = cli.exec(opts);
    return parseJsonlOutput(result.getStdout());
}
```

Add `defaultGlobalOptions()` that maps the existing global config fields and:

```java
if (config.isNoAltScreen()) opts.noAltScreen(true);
```

Route default `startSession` overloads through `defaultGlobalOptions()`.

- [x] **Step 6: Re-run focused tests and verify GREEN**

```bash
mvn -q -Dtest=CodexClientTest test
```

- [x] **Step 7: Run full 1.0.x verification**

```bash
mvn -B --no-transfer-progress clean verify
```

- [x] **Step 8: Commit**

```bash
git add src/main/java/io/github/easy4j/codex/CodexClient.java         src/test/java/io/github/easy4j/codex/CodexClientTest.java
git commit -m "fix(client): honor CLI output and interactive defaults"
```

---

## Task 3: Port Shared CLI Fixes to feature/2.0.x and feature/3.0.x

**Files:**
- Modify on both branches:
  - `src/main/java/io/github/easy4j/codex/CodexClient.java`
  - `src/main/java/io/github/easy4j/codex/cli/CodexCliExecutor.java`
  - matching tests

- [x] **Step 1: Port Task 1 tests to 2.0.x and verify RED**
- [x] **Step 2: Port Task 1 implementation to 2.0.x**
- [x] **Step 3: Verify Task 1 GREEN on 2.0.x**
- [x] **Step 4: Port Task 2 tests to 2.0.x and verify RED**
- [x] **Step 5: Port Task 2 implementation to 2.0.x and verify GREEN**
- [x] **Step 6: Repeat RED/GREEN independently on 3.0.x**

3.0.x commands:

```bash
./mvnw -q -Dtest=CodexCliExecutorTest test
./mvnw -q -Dtest=CodexClientTest test
```

- [x] **Step 7: Run full verification on both branches**

2.0.x:

```bash
mvn -B --no-transfer-progress clean verify
```

3.0.x:

```bash
./mvnw -B --no-transfer-progress clean verify
```

- [x] **Step 8: Commit each branch separately**

---

## Task 4: Canonicalize App Server Protocol Names on feature/2.0.x

**Files:**
- Create: `src/main/java/io/github/easy4j/codex/appserver/CodexAppServerProtocol.java`
- Modify: `CodexAppServerRpc.java`
- Modify: `CodexAppServerTurn.java`
- Modify: `CodexAppServerClient.java`
- Test: `CodexAppServerTurnTest.java`
- Test: `CodexAppServerClientE2ETest.java`

- [x] **Step 1: Write a failing generic-RPC handshake test**

Require exact order:

```text
initialize
notifications/initialized
thread/list
```

Current generic RPC emits `initialized`, so RED is expected.

- [x] **Step 2: Run focused test and verify RED**

```bash
mvn -q -Dtest=CodexAppServerClientE2ETest#shouldSendInitializeHandshake test
```

- [x] **Step 3: Add `CodexAppServerProtocol`**

Include only methods/notifications already used by the SDK.

- [x] **Step 4: Replace free-form protocol strings**

Correct generic RPC notification to:

```java
payload.put("method", CodexAppServerProtocol.INITIALIZED);
```

- [x] **Step 5: Re-run focused E2E test and verify GREEN**
- [x] **Step 6: Run all App Server tests**

```bash
mvn -q -Dtest='io.github.easy4j.codex.appserver.*Test' test
```

- [x] **Step 7: Commit**

---

## Task 5: Serialize WebSocket Writes and Surface Send Failures

**Files:**
- Create: `src/main/java/io/github/easy4j/codex/appserver/CodexWebSocketSender.java`
- Modify: `CodexAppServerRpc.java`
- Modify: `CodexAppServerTurn.java`
- Test: focused sender test plus App Server turn/client tests

**Interfaces:**
- Produces: `CompletionStage<WebSocket> send(String text)`

- [x] **Step 1: Write failing sender-order test**

Second send must not reach the socket before the first send future completes.

- [x] **Step 2: Write failing send-error propagation test**

A failed send must make the returned stage exceptional and must not emit a later frame.

- [x] **Step 3: Run tests and verify RED**

- [x] **Step 4: Implement minimal serialized sender**

```java
final class CodexWebSocketSender {
    private final WebSocket socket;
    private CompletionStage<WebSocket> tail =
            CompletableFuture.completedFuture(null);

    synchronized CompletionStage<WebSocket> send(String text) {
        CompletionStage<WebSocket> next =
                tail.thenCompose(ignored -> socket.sendText(text, true));
        tail = next;
        return next;
    }
}
```

Do not hide send failures.

- [x] **Step 5: Integrate into `CodexAppServerRpc`**

`notifications/initialized` must finish sending before the main request is sent.

- [x] **Step 6: Integrate into `CodexAppServerTurn`**

`notifications/initialized` must finish sending before `thread/start|resume`.

- [x] **Step 7: Run focused tests and verify GREEN**
- [x] **Step 8: Run App Server E2E suite**
- [x] **Step 9: Commit**

---

## Task 6: Add Real Agent Message Delta Streaming Without Duplication

**Files:**
- Create: `CodexAppServerListener.java`
- Modify: `AppServerTurnRequest.java`
- Modify: `CodexAppServerTurn.java`
- Modify: `FakeCodexAppServer.java`
- Test: `CodexAppServerTurnTest.java`
- Test: `CodexAppServerClientE2ETest.java`

- [x] **Step 1: Write failing real-delta test**

Feed:

```json
{"method":"item/agentMessage/delta","params":{"threadId":"th_1","turnId":"turn_1","itemId":"item_1","delta":"你"}}
{"method":"item/agentMessage/delta","params":{"threadId":"th_1","turnId":"turn_1","itemId":"item_1","delta":"好"}}
```

Assert callbacks receive `["你", "好"]`.

- [x] **Step 2: Write failing de-duplication test**

After streamed deltas, feed completed message `你好` and assert final content remains exactly `你好`.

- [x] **Step 3: Write failing legacy fallback test**

No delta notifications + completed agent message must retain existing callback behavior.

- [x] **Step 4: Run focused tests and verify RED**

- [x] **Step 5: Add additive listener API**

```java
public interface CodexAppServerListener {
    default void onTurnStarted(String turnId) {}
    default void onTextDelta(String delta) {}
    default void onItemCompleted(String itemType, String content) {}
    default void onTokenUsage(String rawJson) {}
    default void onWarning(String rawJson) {}
}
```

Add optional listener to `AppServerTurnRequest`.

- [x] **Step 6: Implement streamed-item tracking**

Track streamed item IDs. Real deltas append content and invoke both legacy callback and listener. Completed messages are fallback only for unstreamed items.

- [x] **Step 7: Update fake server to emit real delta frames**
- [x] **Step 8: Run unit/E2E tests and verify GREEN**
- [x] **Step 9: Commit**

---

## Task 7: Serialize Same-Session Turns

**Files:**
- Create: `SessionExecutionCoordinator.java`
- Modify: `CodexAppServerClient.java`
- Modify: `FakeCodexAppServer.java`
- Test: client unit/E2E tests

**Interfaces:**
- Produces: `<T> CompletableFuture<T> submit(String sessionKey, Supplier<CompletableFuture<T>> task)`

- [x] **Step 1: Write failing same-key serialization test**

Second same-key task must not start until first completes.

- [x] **Step 2: Write failing different-key concurrency test**

Different keys must both start before either completes.

- [x] **Step 3: Write failing cleanup test**

Coordinator active-key count must return to zero after completion/exception.

- [x] **Step 4: Run focused tests and verify RED**

- [x] **Step 5: Implement minimal coordinator**

Use a per-key tail future in a `ConcurrentHashMap`. Remove with compare/remove against the exact tail; never hold a global lock while the task runs.

- [x] **Step 6: Integrate into `runTurnAsync`**

Blank session key bypasses coordinator. Non-blank key submits via coordinator.

- [x] **Step 7: Add E2E concurrency tests**
- [x] **Step 8: Run tests and verify GREEN**
- [x] **Step 9: Commit**

---

## Task 8: Introduce Thread Mapping Store Seam

**Files:**
- Create: `ThreadMappingStore.java`
- Modify: `ThreadMappingCache.java`
- Modify: `CodexAppServerClient.java`
- Modify: `CodexAppServerTurn.java`
- Test: cache/client tests

**Interfaces:**

```java
public interface ThreadMappingStore {
    String get(String sessionKey);
    void put(String sessionKey, String threadId);
    void remove(String sessionKey);
}
```

- [x] **Step 1: Write failing remove/store-contract tests**
- [x] **Step 2: Run and verify RED**
- [x] **Step 3: Make `ThreadMappingCache` implement the interface**
- [x] **Step 4: Add additive store-injection constructor**

```java
public CodexAppServerClient(CodexAppServerConfig config) {
    this(config, new ThreadMappingCache(config.getMaxSessionMappings()));
}

public CodexAppServerClient(
        CodexAppServerConfig config,
        ThreadMappingStore threadMappingStore) {
    // existing initialization
}
```

- [x] **Step 5: Change turn dependency from concrete cache to interface**
- [x] **Step 6: Run focused tests and verify GREEN**
- [x] **Step 7: Commit**

---

## Task 9: Correct Turn Completion Semantics

**Files:**
- Modify: `AppServerTurnResult.java`
- Modify: `CodexAppServerTurn.java`
- Test: `CodexAppServerTurnTest.java`

- [x] **Step 1: Write failing completion-status test**

```json
{"method":"turn/completed","params":{"turn":{"status":"completed"}}}
```

Expected:

```java
assertEquals("completed", result.getFinishReason());
```

- [x] **Step 2: Write fallback test**

Missing status/reason → `completed`.

- [x] **Step 3: Run and verify RED**
- [x] **Step 4: Implement tolerant status extraction**

Try:

```text
params.turn.status
params.status
params.reason
fallback: completed
```

- [x] **Step 5: Verify GREEN**
- [x] **Step 6: Commit**

---

## Task 10: Verify Real 2.0.x App Server Behavior

- [x] **Step 1: Run full 2.0.x build**

```bash
mvn -B --no-transfer-progress clean verify
```

- [x] **Step 2: If a real app-server endpoint is available, run opt-in integration**（已执行：从 research/codex 源码构建本地 codex 二进制，`codex app-server --listen ws://127.0.0.1:18181` 起真实 daemon；2.0.x/3.0.x 真实集成各 2/2 通过——initialize 握手、thread/list、thread.id 嵌套解析、turn 全链路无协议层错误）

```bash
mvn -Dcodex.real.base-url=<url>     -Dcodex.real.token=<optional-token>     -Dtest=CodexAppServerRealIntegrationTest test
```

- [x] **Step 3: Any real-wire failure must first become a failing regression test**
- [x] **Step 4: Record fresh test/build totals**

---

## Task 11: Forward-Port Canonical App Server Changes to feature/3.0.x

**Files:**
- Same shared App Server files/tests as Tasks 4–9.
- Add `CodexAppServerRealIntegrationTest.java` if absent.

- [x] **Step 1: Port tests first**
- [x] **Step 2: Run focused 3.0.x tests and verify RED**

```bash
./mvnw -q -Dtest='io.github.easy4j.codex.appserver.*Test' test
```

- [x] **Step 3: Port production behavior**

Only adapt Jackson packages:

```text
com.fasterxml.jackson.* → tools.jackson.*
```

and existing JDK 21 syntax where appropriate.

- [x] **Step 4: Verify focused GREEN**
- [x] **Step 5: Run full 3.0.x build**

```bash
./mvnw -B --no-transfer-progress clean verify
```

- [x] **Step 6: Commit**

---

## Task 12: Documentation Alignment

**Files:**
- `README.md`
- `README.zh-CN.md`

- [x] **Step 1: Document CLI config truth**
  - independent probe timeout
  - `jsonOutput` behavior
  - `execAndParse` forcing JSON
  - `noAltScreen` interactive default
- [x] **Step 2: Document App Server semantics**
  - initialize handshake
  - same-session serialization
  - true text delta streaming
  - listener API
  - injectable thread mapping store
  - request-scoped WebSocket lifetime
- [x] **Step 3: Verify every new API name against source**
- [x] **Step 4: Commit documentation separately**

---

## Task 13: Final Three-Branch Verification

- [x] **Step 1: Verify 1.0.x**

```bash
mvn -B --no-transfer-progress clean verify
```

- [x] **Step 2: Verify 2.0.x**

```bash
mvn -B --no-transfer-progress clean verify
```

- [x] **Step 3: Verify 3.0.x**

```bash
./mvnw -B --no-transfer-progress clean verify
```

- [x] **Step 4: Confirm baselines**

```text
1.0.x → Java 8, Maven 3.9.16, Jackson 2.18.9
2.0.x → Java 17, Maven 3.9.16, Jackson 2.22.1
3.0.x → Java 21, Maven 4.0.0-rc-5, Jackson 3.2.1
```

- [x] **Step 5: Push only verified branch commits**
- [x] **Step 6: Inspect GitHub Actions for each pushed HEAD**
- [x] **Step 7: If any CI fails, inspect logs and re-enter a TDD fix cycle**
- [x] **Step 8: Produce completion report with SHA, test totals, build result, CI result, and real-integration skips**

---

## Self-Review Results

- **Spec coverage:** all acceptance criteria map to Tasks 1–13.
- **Placeholder scan:** no unfinished implementation placeholders are present.
- **Type consistency:** listener/store/coordinator names are defined before use and remain consistent.
- **Review focus coverage:**
  - probe timeout isolation → Task 1
  - JSON semantics → Task 2
  - WebSocket sequencing/failure → Task 5
  - same-session concurrency/cleanup → Task 7
  - streaming de-duplication → Task 6
- **Scope control:** persistent WebSocket transport, full upstream schema generation, Redis/DB stores, and mass typed coverage remain out of scope.
