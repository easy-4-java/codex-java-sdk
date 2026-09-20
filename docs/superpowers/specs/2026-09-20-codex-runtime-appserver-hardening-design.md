# Codex Java SDK Runtime & App Server Hardening Design

> Status: Approved direction, implementation pending
>
> Canonical branch for this design: `feature/2.0.x`
>
> Applies to: `feature/1.0.x`, `feature/2.0.x`, `feature/3.0.x`

## 1. Goal

Strengthen `codex-java-sdk` from a broad Codex CLI wrapper into a reliable Java runtime adapter for the official Codex CLI and Codex app-server without changing the product boundary into a direct OpenAI HTTP API SDK.

The SDK keeps two runtime routes:

1. **CLI route** — typed Java APIs mapped to the local `codex` executable.
2. **App Server route** — typed Java APIs mapped to the Codex JSON-RPC/WebSocket app-server protocol.

The design must preserve the existing JDK/version-line strategy while eliminating protocol drift, duplicate transport behavior, silent configuration mismatches, and same-session concurrency races.

## 2. Version-Line Ownership

### 2.1 feature/1.0.x

Purpose: modern Codex CLI capability on JDK 8.

Technology baseline:

- JDK 8
- Maven 3.9.16
- Jackson 2.18.9
- Apache Commons Exec 1.6.0

Supported layers:

- CLI command facade
- CLI subprocess runtime
- CLI JSONL parsing

Not supported:

- Java `java.net.http.WebSocket` app-server transport

The 1.0.x line receives **shared CLI fixes only**. App-server packages must not be backported.

### 2.2 feature/2.0.x

Purpose: production-oriented functional baseline.

Technology baseline:

- JDK 17
- Maven 3.9.16
- Jackson 2.22.1
- Apache Commons Exec 1.6.0

Ownership:

- Canonical implementation for App Server protocol behavior.
- First branch to receive app-server protocol fixes and real-wire validation.
- Shared CLI behavior must remain aligned with 1.0.x and 3.0.x where JDK compatibility permits.

### 2.3 feature/3.0.x

Purpose: forward-looking runtime baseline.

Technology baseline:

- JDK 21
- Maven 4.0.0-rc-5
- Jackson 3.2.1
- Apache Commons Exec 1.6.0

Ownership:

- Canonical line for JDK 21 / Maven 4 / Jackson 3 adaptation.
- Receives App Server behavior from 2.0.x through controlled forward-porting.
- Must not silently diverge in protocol semantics.

## 3. Architecture

The target architecture is:

```text
codex-java-sdk
│
├── CLI API
│   ├── CodexClient
│   ├── CodexCli
│   ├── ExecOptions
│   └── GlobalOptions
│
├── CLI Runtime
│   └── CodexCliExecutor
│       ├── argv
│       ├── stdin
│       ├── stdout/stderr
│       ├── UTF-8
│       ├── timeout
│       └── exit code
│
└── App Server Runtime
    ├── Client
    │   └── CodexAppServerClient
    ├── Protocol
    │   ├── method / notification constants
    │   ├── JSON-RPC request/response/error semantics
    │   └── initialize handshake
    ├── Transport
    │   └── serialized WebSocket send path
    ├── Thread
    │   ├── AppServerThread
    │   └── ThreadMappingStore
    ├── Turn
    │   ├── AppServerTurnRequest
    │   ├── AppServerTurnResult
    │   └── CodexAppServerTurn
    └── Events
        ├── turn lifecycle
        ├── item lifecycle
        ├── agent message delta
        └── warnings / usage
```

This design does not require a large package restructure in the first implementation. New boundaries should be introduced incrementally to avoid unnecessary public API churn.

## 4. Protocol Canonicalization

### 4.1 Single protocol authority

Protocol method names must not be duplicated as free-form literals across `CodexAppServerTurn` and `CodexAppServerRpc`.

Introduce an internal protocol constants class, initially package-private, for methods that are used by the SDK:

```java
final class CodexAppServerProtocol {
    static final String INITIALIZE = "initialize";
    static final String INITIALIZED = "notifications/initialized";

    static final String THREAD_START = "thread/start";
    static final String THREAD_RESUME = "thread/resume";
    static final String THREAD_LIST = "thread/list";
    static final String THREAD_READ = "thread/read";
    static final String THREAD_FORK = "thread/fork";
    static final String THREAD_ARCHIVE = "thread/archive";
    static final String THREAD_UNARCHIVE = "thread/unarchive";
    static final String THREAD_DELETE = "thread/delete";

    static final String TURN_START = "turn/start";
    static final String TURN_STEER = "turn/steer";
    static final String TURN_INTERRUPT = "turn/interrupt";

    static final String TURN_STARTED = "turn/started";
    static final String TURN_COMPLETED = "turn/completed";
    static final String ITEM_STARTED = "item/started";
    static final String ITEM_COMPLETED = "item/completed";
    static final String AGENT_MESSAGE_DELTA = "item/agentMessage/delta";
    static final String TOKEN_USAGE_UPDATED = "thread/tokenUsage/updated";
    static final String ERROR = "error";
}
```

Do not attempt to model the entire upstream protocol table in this task. `execRpc(method, params)` remains the escape hatch.

### 4.2 Initialize handshake

Both turn-scoped and generic-RPC connections must use the same semantic sequence:

```text
WebSocket open
  → initialize request
  → initialize response
  → notifications/initialized notification
  → first business request
```

The SDK must use `notifications/initialized`, matching the current official Codex app-server behavior.

The generic RPC route may retain tolerant behavior toward legacy daemons that reject `initialize`, but the behavior must be explicit and tested.

The turn route must remain strict for current Codex app-server protocol because real-wire validation shows requests before initialization are rejected.

### 4.3 Thread ID compatibility

Thread start/resume parsing must support both:

Current shape:

```json
{
  "result": {
    "thread": {
      "id": "..."
    }
  }
}
```

Legacy compatible shapes:

```json
{
  "result": {
    "threadId": "..."
  }
}
```

and:

```json
{
  "result": {
    "thread_id": "..."
  }
}
```

The current nested shape is preferred. Legacy shapes are fallback compatibility only.

## 5. WebSocket Transport Semantics

### 5.1 Serialized writes

All writes on one WebSocket connection must be serialized.

No component may fire multiple `sendText(...)` calls without chaining completion.

Required sequence:

```text
send initialize
  await completion
receive initialize result
  send notifications/initialized
    await completion
  send thread/start or main RPC
    await completion
...
```

Implement a small package-private send coordinator or equivalent chained `CompletionStage<WebSocket>` mechanism. Avoid introducing a general networking framework.

### 5.2 Write failures

A WebSocket write failure must fail the owning turn/RPC immediately.

It must not remain pending until `readTimeoutMillis`.

Error propagation must retain the real cause where possible.

### 5.3 Connection lifetime

For this iteration, keep the current request-scoped connection strategy:

- one WebSocket per turn
- one WebSocket per generic RPC

Persistent multiplexed connections are explicitly out of scope.

## 6. Turn Event Model and Streaming

### 6.1 Current problem

The existing `onDelta` callback is driven by `item/completed`, which represents completed message items rather than true incremental text deltas.

Current Codex app-server exposes `item/agentMessage/delta`.

### 6.2 Compatibility goal

Do not break existing consumers in this optimization.

Keep:

```java
Consumer<String> onDelta;
```

but redefine its preferred source:

1. If `item/agentMessage/delta` events are observed, invoke `onDelta` with each real `delta`.
2. Do not invoke duplicate full-message deltas from `item/completed` for an item that already streamed.
3. For legacy servers that do not emit message delta notifications, retain fallback delivery from completed agent-message items.

### 6.3 Listener evolution

Introduce an optional listener abstraction without removing existing callbacks:

```java
public interface CodexAppServerListener {
    default void onTurnStarted(String turnId) {}
    default void onTextDelta(String delta) {}
    default void onItemCompleted(String itemType, String content) {}
    default void onTokenUsage(String rawJson) {}
    default void onWarning(String rawJson) {}
}
```

The exact listener payloads may remain minimal in this task. The primary requirement is to stop adding one new callback field per protocol notification.

Existing `onDelta` and `onTurnStarted` remain supported and are bridged to the listener path.

## 7. Same-Session Concurrency

### 7.1 Contract

The client supports concurrency across independent sessions.

```text
session A ── concurrent ── session B
session B ── concurrent ── session C
```

For the same non-blank `sessionKey`, turns must execute serially:

```text
A1 → A2 → A3
```

This prevents:

- two simultaneous `thread/start` calls for an unmapped session
- competing cache updates
- multiple uncontrolled active turns on the same resumed thread

Turns without a session key remain independently concurrent.

### 7.2 Implementation direction

Add an internal session execution coordinator owned by `CodexAppServerClient`.

A lightweight implementation may maintain:

```text
sessionKey → tail CompletableFuture
```

A new turn chains after the previous tail for that key.

Completed keys must be removable so the coordinator does not grow without bound.

The coordinator must not hold a global lock while the turn executes.

## 8. Thread Mapping

### 8.1 Store abstraction

Replace hard coupling to `ThreadMappingCache` with a small internal/publicly-extensible store contract:

```java
public interface ThreadMappingStore {
    String get(String sessionKey);
    void put(String sessionKey, String threadId);
    void remove(String sessionKey);
}
```

Default implementation:

```text
InMemoryThreadMappingStore
  → bounded access-order LRU
```

For source compatibility, the existing `ThreadMappingCache` may remain as the default implementation or become a deprecated compatibility wrapper. Avoid unnecessary breaking renames in this optimization.

### 8.2 Persistence

Redis/database implementations are out of scope.

The design only creates the seam so external runtimes may provide persistent storage later.

## 9. CLI Configuration Correctness

The following fields currently exist in `CodexClientConfig` but are not correctly represented by runtime behavior.

### 9.1 localProbeTimeoutSeconds

`probe()` must honor `localProbeTimeoutSeconds` rather than the normal command timeout.

Implement a timeout-aware internal executor path instead of mutating shared configuration.

Expected behavior:

```text
normal command → localTimeoutSeconds
probe          → localProbeTimeoutSeconds
```

### 9.2 jsonOutput

`CodexClient.defaultOptions(prompt)` must honor `config.isJsonOutput()`.

Do not hardcode `.json(true)`.

`execAndParse()` must explicitly require JSON output for its own invocation regardless of the general default, because it promises parsed JSONL events.

### 9.3 noAltScreen

`noAltScreen` is a global interactive option and must be propagated when `CodexClient` builds default global options for interactive commands.

If the current client has no default-global-options builder, introduce one and route `startSession` through it while preserving existing overload behavior.

## 10. CLI Runtime Invariants

The following behavior is already correct and must not regress:

- argv values are passed without shell quoting injection
- UTF-8 stdout/stderr decoding
- stdin is explicitly closed after payload
- non-zero process exit preserves real exit code
- non-zero process exit preserves stdout/stderr
- watchdog timeout maps to timeout result
- missing executable maps to transport/spawn failure result

1.0.x must keep the Java 8 UTF-8 implementation compatible with Java 8 APIs.

2.0.x and 3.0.x may use `StandardCharsets.UTF_8`.

## 11. App Server Result Semantics

### 11.1 finishReason

Do not hardcode `finishReason = "stop"` as a protocol truth.

For source compatibility, the field may remain a string in this iteration.

Set it from the actual terminal status/reason when present. Otherwise use a documented neutral fallback such as `"completed"`.

Interrupted and failed turns must not be represented as `"stop"`.

### 11.2 Raw compatibility

Unknown notifications remain non-fatal unless they are explicit server errors.

The SDK must continue to ignore unknown notifications at debug level so newer Codex servers do not break older SDK clients.

## 12. Testing Strategy

All behavior changes use TDD.

### 12.1 CLI tests — all three branches

Add regression tests for:

- probe uses probe timeout, not command timeout
- `jsonOutput=false` affects normal `exec`
- `execAndParse` still forces JSON mode
- `noAltScreen=true` appears in interactive default args

Run the complete test suite on each branch.

### 12.2 App Server tests — 2.0.x canonical

Add tests for:

- both turn and generic RPC use `notifications/initialized`
- business request is not sent until initialized notification send completes
- WebSocket send failure completes the owning operation exceptionally
- real nested `result.thread.id`
- legacy top-level thread id
- real `item/agentMessage/delta` invokes streaming callback
- completed item does not duplicate already-streamed text
- completed item remains fallback for legacy/no-delta server
- same `sessionKey` turns serialize
- different `sessionKey` turns overlap/concurrently start
- coordinator entries are cleaned after completion
- finish reason/status is not hardcoded to stop

Keep fake-server E2E tests.

Keep opt-in real integration tests guarded by `-Dcodex.real.base-url`.

### 12.3 App Server tests — 3.0.x

Forward-port the full 2.0.x protocol test set, adapting only Jackson/JDK APIs.

3.0.x must not have fewer protocol contract tests than 2.0.x for shared App Server behavior.

## 13. Branch Synchronization Rules

### Shared CLI changes

Canonical behavior must be implemented and verified on all three lines.

Porting order for this project phase:

```text
feature/1.0.x
feature/2.0.x
feature/3.0.x
```

Each branch keeps its own JDK/Jackson syntax.

### App Server changes

Canonical behavior:

```text
feature/2.0.x
      ↓
forward-port
      ↓
feature/3.0.x
```

Never assume 3.0.x is automatically the most functionally current line.

## 14. Public API Compatibility

This optimization must avoid unnecessary source-breaking changes.

Keep existing public classes and methods where practical:

- `CodexClient`
- `CodexCli`
- `CodexCliExecutor`
- `CodexCliResult`
- `CodexAppServerClient`
- `CodexAppServerConfig`
- `AppServerTurnRequest`
- `AppServerTurnResult`
- `AppServerThread`
- `ThreadMappingCache`

New abstractions should be additive.

If an existing API is semantically misleading, deprecate only when a safe replacement is introduced. Do not rename `startSession` in this phase.

## 15. Out of Scope

The following are explicitly not part of this optimization:

- direct OpenAI HTTP API client
- implementing all upstream app-server methods as typed Java methods
- persistent WebSocket multiplexing
- Redis/database mapping implementations
- removing legacy CLI APIs
- JDK baseline changes
- Maven baseline changes
- Jackson baseline changes
- large package reorganization
- generated Java types for the entire upstream JSON schema

## 16. Acceptance Criteria

The optimization is accepted when all conditions hold:

1. `feature/1.0.x` remains JDK 8 and full CLI tests pass.
2. `feature/2.0.x` remains JDK 17 and is the canonical App Server implementation.
3. `feature/3.0.x` remains JDK 21 / Maven 4 / Jackson 3 and matches 2.0.x App Server semantics.
4. Both App Server transport paths use the same official initialized notification name.
5. WebSocket writes are serialized and write failures surface immediately.
6. Same-session turns serialize; different sessions remain concurrent.
7. Real agent message deltas are supported without duplicate content.
8. `localProbeTimeoutSeconds`, `jsonOutput`, and `noAltScreen` affect runtime behavior.
9. Current CLI process guarantees remain intact.
10. 2.0.x real app-server integration test remains available and 3.0.x gains equivalent coverage.
11. Full branch builds complete successfully.
12. GitHub Actions is green for all modified branches.

## 17. Implementation Order

Implementation will follow this sequence:

1. CLI configuration correctness on 1.0.x / 2.0.x / 3.0.x.
2. App Server protocol constants and handshake unification on 2.0.x.
3. Serialized WebSocket send and immediate send-failure propagation.
4. True message delta streaming with compatibility fallback.
5. Same-session execution coordinator.
6. Mapping-store seam.
7. Result/finish-reason correction.
8. Real integration verification on 2.0.x.
9. Forward-port App Server changes and tests to 3.0.x.
10. Full three-branch verification and CI review.
