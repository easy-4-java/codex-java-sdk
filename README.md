# codex-java-sdk

[English](./README.md) | [简体中文](./README.zh-CN.md)

[![Java](https://img.shields.io/badge/Java-21-orange)](https://github.com/easy-4-java/codex-java-sdk) [![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://www.apache.org/licenses/LICENSE-2.0.txt)

> Java SDK for the [Codex CLI](https://github.com/openai/codex) with two
> integration routes: a subprocess wrapper that drives the local `codex`
> agent (exec, interactive sessions, session resume / fork / archive, doctor,
> review), and a JSON-RPC 2.0 over WebSocket client for a remote Codex
> app-server (`thread/start` → `turn/start` → notification stream).

## Table of Contents

- [1. Project Overview](#1-project-overview)
- [2. Features & Status](#2-features--status)
- [3. Requirements & Compatibility](#3-requirements--compatibility)
- [4. Architecture & Modules](#4-architecture--modules)
- [5. Installation](#5-installation)
- [6. Quick Start](#6-quick-start)
- [7. Configuration](#7-configuration)
- [8. Core Usage / API](#8-core-usage--api)
- [9. Testing & Build](#9-testing--build)
- [10. Versioning & Branches](#10-versioning--branches)
- [11. Contributing & License](#11-contributing--license)

## 1. Project Overview

`codex-java-sdk` lets Java applications integrate the
[Codex CLI](https://github.com/openai/codex) agent (`codex`) through two
routes. Neither route is a direct OpenAI API client.

- **CLI route (local subprocess)** — every call maps to a real `codex`
  command line invocation.
- **App-server route (remote long connection)** — a JSON-RPC 2.0 over
  WebSocket client for a running Codex app-server.

The SDK covers:

- **Exec mode** — `codex exec <prompt>` with model, sandbox, JSONL output, web
  search, output files, output schema, images, config overrides and ephemeral runs.
- **Interactive sessions** — `codex [prompt]` and full session lifecycle:
  `resume` / `resumeLast` / `fork` / `archive` / `unarchive`.
- **Parsed models** — `CodexEvent` (JSONL events), `CodexSession`, `CodexDoctorReport`.
- **Utilities** — `doctor`, `review`, `login` / `logout`, MCP management, `update`,
  `features`, shell `completion`.
- **App-server WebSocket route** — `CodexAppServerClient` with per-turn
  connections, `thread/start` / `thread/resume` reuse via a bounded
  `sessionKey → threadId` LRU, streaming agent-message deltas and
  `turn/completed` finalization.

What it is **not**:

- Not an OpenAI API client (no direct HTTP calls to the OpenAI API).
- Not a replacement for the `codex` binary — the CLI must be installed and runnable
  (local route), or a Codex app-server must be reachable (WebSocket route).

Typical scenarios:

| Scenario | What you use |
| :--- | :--- |
| One-shot coding task | `CodexClient.exec(prompt)` |
| Machine-readable event stream | `execAndParse(prompt)` → `List<CodexEvent>` |
| Long-running interactive agent | `startSession(prompt)` / `resumeSession(sessionId)` |
| Reproduce a session in a sandbox | `forkSession(sessionId)` / `execResume(sessionId, prompt)` |
| Environment diagnostics | `doctorSummary()` / `doctorJson()` |
| Remote agent with session continuity | `CodexAppServerClient.runTurn(request)` with `sessionKey` |

## 2. Features & Status

| Capability | Status | Notes |
| :--- | :--- | :--- |
| `codex exec` non-interactive mode | Active development | `exec`, `exec(model)`, `exec(ExecOptions)` |
| Exec variants | Active development | `execInDir`, `execEphemeral`, `execWithSearch`, `execToFile`, `execWithSchema`, `execWithImage`, `execWithConfigOverrides`, `execDangerously`, `execBypassHookTrust`, `execWithEnable` / `execWithDisable` |
| JSONL event parsing | Active development | `execAndParse(prompt)` → `List<CodexEvent>` |
| Interactive sessions | Active development | `startSession()`, `startSession(prompt)`, `startSession(GlobalOptions, prompt)` |
| Session lifecycle | Active development | `resumeSession`, `resumeLastSession`, `forkSession`, `forkLastSession`, `archiveSession`, `unarchiveSession`, `execResume` |
| Doctor & review | Active development | `doctor`, `doctorJson`, `doctorSummary`, `review`, `reviewCommit`, `reviewBase` |
| Auth / MCP / misc | Active development | `login`, `loginWithApiKey`, `loginWithAccessToken`, `loginDeviceAuth`, `loginStatus`, `logout`, `mcpList` / `mcpAdd` / `mcpGet` / `mcpRemove` / `mcpLogin` / `mcpLogout`, `update`, `features`, `completion`, `app` |
| Session admin | Active development | `archiveSession`, `unarchiveSession`, `queue`, `deleteSession`, `deleteSessionForce`, `agents`, `migrateRollouts` |
| App-server WebSocket route | Active development | `CodexAppServerClient.runTurn` / `runTurnAsync`, `thread/start` / `thread/resume`, agent-message deltas, `sessionKey → threadId` LRU (1000) |
| App-server protocol surface | Active development | `thread/list` / `read` / `fork` / `archive` / `unarchive` / `delete`, `turn/interrupt` / `turn/steer`, `model/list`-style escape hatch `execRpc`; `turnId` exposed via `onTurnStarted` + `AppServerTurnResult` |
| CLI typed additions | Active development | `debugModels(Bundled)` / `debugPromptInput`, `mcpAddUrl(WithBearer)`, `pluginAdd/List/Remove` + `pluginMarketplace*`, `cloudExec` / `cloudList`, `featuresEnable/Disable/List`, `reviewPrompt` |
| Config model | Active development | `CodexClientConfig` POJO (plain, Spring-bindable), `CodexAppServerConfig` POJO |

> **Note**: `codex mcp-server` was removed upstream — `CodexClient.mcpServer()`
> is deprecated in favour of `appServer(...)`. `ExecOptions` additionally
> supports `--ignore-rules` / `--ignore-user-config`, and `GlobalOptions`
> supports `--remote` / `--remote-auth-token-env` for daemon-backed TUI runs.

> **Assumption**: the capability statuses above reflect the current state of
> the active branch; the module is under active development.

## 3. Requirements & Compatibility

| Requirement | Version / Notes |
| :--- | :--- |
| JDK | 21+ |
| Maven | 3.0+ (enforced; Maven Wrapper `./mvnw` included) |
| Codex CLI | Local route: `codex` must be installed and available (`localExecutable` configures the path) |
| Codex app-server | WebSocket route only: a reachable app-server (`baseUrl` accepts ws/wss/http/https) |

> **Note**: the app-server WebSocket route uses the JDK built-in
> `java.net.http.HttpClient` (JDK 11+). It is available on the `feature/2.0.x`
> and `feature/3.0.x` lines; the `feature/1.0.x` (JDK 8) line ships the CLI
> route only.

Version lines:

| Branch | JDK | Version |
| :--- | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

## 4. Architecture & Modules

```text
+------------------+   +---------------------------------------------+
| Java application |   | codex-java-sdk                               |
|                  |-->|  Route 1 (local): CodexClient (facade)       |
| prompt / options |   |    | CodexCli (command mapping)             |
|                  |   |    |   | CodexCliExecutor                   |
|                  |   |    |   |   `codex` child process            |
|                  |   |    |   CodexCliResult                       |
|                  |   |  Route 2 (remote): CodexAppServerClient      |
|                  |   |    | JSON-RPC 2.0 over WebSocket            |
|                  |   |    | thread/start -> turn/start -> events   |
|                  |   | CodexEvent/CodexSession/CodexDoctorReport    |
+------------------+   +-------------------+-------------------------+
                                           |
                                           v
                     +-------------------------------------------+
                     | Local `codex` CLI (route 1) or remote     |
                     | Codex app-server (route 2)                |
                     +-------------------------------------------+
```

Single-module Maven project (`packaging: jar`). No child modules.

| Artifact | Responsibility |
| :--- | :--- |
| `io.github.easy4j:codex-java-sdk` | CLI facade, command mapping, subprocess executor, WebSocket app-server client, results & parsed models |

Key packages:

| Package | Contents |
| :--- | :--- |
| `io.github.easy4j.codex` | `CodexClient`, `CodexClientConfig` |
| `io.github.easy4j.codex.cli` | `CodexCli`, `CodexCliExecutor`, `CodexCliResult` |
| `io.github.easy4j.codex.appserver` | `CodexAppServerClient`, `CodexAppServerConfig`, `AppServerTurnRequest`, `AppServerTurnResult`, `CodexAppServerListener`, `ThreadMappingStore`, `ThreadMappingCache`, `CodexAppServerException` |
| `io.github.easy4j.codex.model` | `CodexEvent`, `CodexSession`, `CodexDoctorReport` |

## 5. Installation

The project is **not yet published to Maven Central**. Snapshots/releases are
distributed through the Aliyun Maven repository and GitHub Releases.

Maven:

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>codex-java-sdk</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.easy4j:codex-java-sdk:3.0.x.x.20260630-SNAPSHOT'
```

## 6. Quick Start

```java
import io.github.easy4j.codex.CodexClient;
import io.github.easy4j.codex.CodexClientConfig;
import io.github.easy4j.codex.cli.CodexCliResult;

public class CodexDemo {

    public static void main(String[] args) {
        CodexClientConfig config = new CodexClientConfig();
        config.setLocalExecutable("codex");   // or an absolute path
        config.setLocalTimeoutSeconds(600);

        try (CodexClient client = new CodexClient(config)) {
            CodexCliResult result = client.exec("Write a Java hello world");
            System.out.println("exit=" + result.getExitCode());
            System.out.println(result.getStdout());
        }
    }
}
```

Expected result: `codex exec "Write a Java hello world"` runs locally;
`result.getExitCode()` is `0` on success and `result.getStdout()` contains the
agent's answer.

## 7. Configuration

`CodexClientConfig` is a plain POJO (Spring `@ConfigurationProperties`-bindable).
There is no configuration file of its own. Key fields:

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `localExecutable` | String | `codex` | CLI executable name or absolute path |
| `localTimeoutSeconds` | int | `600` | Command execution timeout (seconds) |
| `localProbeTimeoutSeconds` | int | `5` | Timeout for the CLI availability probe |
| `defaultModel` | String | - | Default model |
| `defaultSandbox` | String | - | Sandbox mode (`read-only`, `workspace-write`, `danger-full-access`) |
| `defaultApprovalPolicy` | String | - | Approval policy (`untrusted`, `on-request`, `never`) |
| `defaultProfile` | String | - | Default config profile |
| `ossProvider` / `localProvider` | boolean / String | - | OSS provider / local provider (`lmstudio`, `ollama`) |
| `skipGitRepoCheck` | boolean | `false` | Skip git repo checks |
| `ephemeral` | boolean | `false` | Ephemeral session (no persistence) |
| `jsonOutput` | boolean | `true` | JSONL output |
| `outputSchema` | String | - | Output schema file path |
| `search` | boolean | `false` | Enable web search |
| `image` | String | - | Image file path |
| `configOverrides` | String[] | - | Config overrides (`-c key=value`) |
| `outputFile` | String | - | Output file path (`output-last-message`) |
| `workingDir` | String | - | Working directory |
| `dangerouslyBypassApprovalsAndSandbox` | boolean | `false` | Skip all approvals and sandbox (dangerous) |
| `dangerouslyBypassHookTrust` | boolean | `false` | Skip hook trust checks |
| `strictConfig` | boolean | `false` | Fail on unknown config fields |
| `enable` / `disable` | String[] | - | Features to enable / disable |
| `noAltScreen` | boolean | `false` | Pass `--no-alt-screen` to default interactive sessions |

Runtime semantics:
- `localProbeTimeoutSeconds` applies only to CLI availability probing; normal commands continue to use `localTimeoutSeconds`.
- `jsonOutput` controls normal `exec` calls; `execAndParse` always forces `--json` because it promises parsed JSONL events.
- `noAltScreen` is propagated through the default interactive-session options.

### 7.1 `CodexAppServerConfig` (app-server WebSocket route)

> Available on `feature/2.0.x` (JDK 17) and `feature/3.0.x` (JDK 21). The JDK 8 `feature/1.0.x` line intentionally remains CLI-only.

> **Upgrade notes (3.0.x.x.20260630+)**: CLI-route arguments are now passed
> to the child process raw — multi-word prompts no longer arrive at `codex`
> wrapped in embedded literal quotes. Non-zero CLI exits now preserve the real
> exit code and both captured streams instead of collapsing to `exitCode=-1`
> with empty output. A bearer token over a plaintext `ws://` connection logs a
> warning; prefer `wss://`.

Plain POJO (Spring `@ConfigurationProperties`-bindable). Field names mirror the
commonly used `CodexEndpoint` binding:

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `baseUrl` | String | - | App-server base URL (`ws://`/`wss://` as-is, `http://`/`https://` upgraded) |
| `token` | String | - | Bearer token sent as `Authorization: Bearer <token>` on the handshake |
| `connectTimeoutMillis` | int | `5000` | TCP/TLS + WebSocket handshake timeout |
| `readTimeoutMillis` | int | `120000` | Upper bound for a whole turn (connect → `turn/completed`) |
| `maxSessionMappings` | int | `1000` | Bound of the `sessionKey → threadId` LRU; evicted sessions start fresh threads |
| `maxFrameChars` | int | `1048576` | Frame accumulation hard cap; oversized server frames fail the turn (`<= 0` = unbounded) |
| `maxContentChars` | int | `1048576` | Per-turn agent-message content cap; excess is truncated with a warning (`<= 0` = unbounded) |

## 8. Core Usage / API

### 8.1 JSONL events

```java
try (CodexClient client = new CodexClient(config)) {
    // codex exec --json <prompt>, parsed into typed events
    List<CodexEvent> events = client.execAndParse("Fix the failing test");
    events.forEach(event -> System.out.println(event.getType() + " -> " + event.getMessage()));
}
```

### 8.2 Session lifecycle

```java
try (CodexClient client = new CodexClient(config)) {
    client.exec("first task");                  // creates a persisted session
    client.resumeSession("session-id");         // resume an interactive session
    client.forkSession("session-id");           // fork into a new session
    client.archiveSession("session-id");        // archive a session
    client.execResume("session-id", "continue");// non-interactive resume
    client.doctorSummary();                     // environment diagnostics
}
```

### 8.3 App-server WebSocket route (remote Codex)

```java
import io.github.easy4j.codex.appserver.AppServerTurnRequest;
import io.github.easy4j.codex.appserver.AppServerTurnResult;
import io.github.easy4j.codex.appserver.CodexAppServerClient;
import io.github.easy4j.codex.appserver.CodexAppServerConfig;

CodexAppServerConfig config = new CodexAppServerConfig();
config.setBaseUrl("ws://codex-host:8081");   // http(s) is upgraded to ws(s) automatically
config.setToken("capability-token");
config.setReadTimeoutMillis(120_000);

try (CodexAppServerClient client = new CodexAppServerClient(config)) {
    AppServerTurnResult result = client.runTurn(AppServerTurnRequest.builder()
            .prompt("Fix the failing test")
            .sessionKey("chat-42")                                  // enables thread/resume reuse
            .onDelta(delta -> System.out.print(delta))              // agentMessage deltas, in order
            .build());
    System.out.println(result.getThreadId() + " -> " + result.getContent());
}
```

### 8.4 App-server protocol operations

```java
try (CodexAppServerClient client = new CodexAppServerClient(config)) {
    List<AppServerThread> threads = client.listThreads(20);
    AppServerThread forked = client.forkThread("th_123");
    client.steerTurn("th_123", "turn_9", "也检查一下测试覆盖率");   // redirect a running turn
    client.interruptTurn("th_123", "turn_9");                      // cancel a running turn
    client.archiveThread("th_123");
    String raw = client.execRpc("model/list", Map.of("limit", 10)); // escape hatch
}
```

Each app-server operation currently uses a request-scoped WebSocket connection.
Every connection performs `initialize` → `notifications/initialized` before
the first business request, and WebSocket writes are serialized so protocol
frames cannot overtake one another. Send failures fail the owning operation
immediately instead of degrading into a later read timeout.

Turns with the same non-blank `sessionKey` are serialized; different session
keys remain concurrent. The default `ThreadMappingCache` is a bounded in-memory
`ThreadMappingStore`; callers that need persistence may inject another store
implementation.

A turn maps to `thread/start` (or `thread/resume`) → `turn/start` →
`item/agentMessage/delta` streaming → `turn/completed`. Existing
`onDelta` callbacks now receive real text deltas when the server emits them;
`item/completed` is retained as a compatibility fallback for servers that do
not stream deltas, without duplicating already-streamed text. The additive
`CodexAppServerListener` exposes turn start, text delta, item completion,
token-usage and warning hooks. Running turns expose their id via
`AppServerTurnRequest.onTurnStarted` / the listener and
`AppServerTurnResult.getTurnId()`.

Unknown notifications are logged at debug level and do not interrupt the turn.
Failures — connection, JSON-RPC error, `turn/failed`, `error`, WebSocket send
failure, premature close or read timeout — surface as
`CodexAppServerException`. Successful completion preserves the server's turn
status when present and otherwise uses `completed` as the neutral fallback.

## 9. Testing & Build

```bash
./mvnw clean verify
```

- The build is configured with the JaCoCo Maven plugin (report + `check` goal with a
  90% line-coverage rule bound to the `verify` phase; `haltOnFailure=false`).
- Each maintained branch ships its own full test suite; the 2.0.x/3.0.x lines include end-to-end WebSocket contract tests against an in-process fake app-server plus an opt-in real app-server integration test.
- CI workflow: `.github/workflows/ci.yml`.

## 10. Versioning & Branches

| Branch | JDK | Version | Notes |
| :--- | :--- | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` | CLI compatibility line; no app-server transport |
| `feature/2.0.x` | 17 | `2.0.x.*` | Canonical App Server protocol/behavior line |
| `feature/3.0.x` | 21 | `3.0.x.*` | JDK 21 / Maven 4 / Jackson 3 forward-port line |

Maintenance policy: shared CLI fixes stay aligned across all three lines.
App Server protocol behavior is validated first on `feature/2.0.x` and then
forward-ported to `feature/3.0.x`; the JDK 8 line intentionally remains
CLI-only. Releases are published to the Aliyun Maven repository and as
GitHub Releases; the project is not yet published to Maven Central.

## 11. Contributing & License

Contributions are welcome — please open issues or pull requests on GitHub.

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
