# codex-java-sdk

[English](./README.md) | [简体中文](./README.zh-CN.md)

[![Java](https://img.shields.io/badge/Java-21-orange)](https://github.com/easy-4-java/codex-java-sdk) [![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://www.apache.org/licenses/LICENSE-2.0.txt)

> [Codex CLI](https://github.com/openai/codex) 的 Java SDK，提供两条集成路线：
> 本地子进程封装（驱动 `codex` 智能体的 exec 非交互执行、交互会话、会话
> resume / fork / archive、doctor、review），以及面向远程 Codex app-server 的
> JSON-RPC 2.0 over WebSocket 长连接客户端（`thread/start` → `turn/start` →
> 通知流）。

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 功能与状态](#2-功能与状态)
- [3. 环境要求与兼容性](#3-环境要求与兼容性)
- [4. 架构与模块](#4-架构与模块)
- [5. 安装](#5-安装)
- [6. 快速开始](#6-快速开始)
- [7. 配置](#7-配置)
- [8. 核心用法 / API](#8-核心用法--api)
- [9. 测试与构建](#9-测试与构建)
- [10. 版本与分支](#10-版本与分支)
- [11. 贡献与许可](#11-贡献与许可)

## 1. 项目概述

`codex-java-sdk` 让 Java 应用通过两条路线集成 [Codex CLI](https://github.com/openai/codex)
智能体（`codex`）。两条路线都不是直连 OpenAI API 客户端。

- **CLI 路线（本地子进程）**——每次调用都对应一次真实的 `codex` 命令行执行。
- **App-server 路线（远程长连接）**——面向运行中的 Codex app-server 的
  JSON-RPC 2.0 over WebSocket 客户端。

SDK 覆盖：

- **Exec 模式** — `codex exec <prompt>`，支持模型、沙箱、JSONL 输出、web 搜索、
  输出文件、输出 Schema、图片、配置覆盖与临时运行。
- **交互式会话** — `codex [prompt]` 与完整会话生命周期：`resume` / `resumeLast` /
  `fork` / `archive` / `unarchive`。
- **解析模型** — `CodexEvent`（JSONL 事件）、`CodexSession`、`CodexDoctorReport`。
- **工具类** — `doctor`、`review`、`login` / `logout`、MCP 管理、`update`、
  `features`、shell `completion`。
- **App-server WebSocket 路线** — `CodexAppServerClient`：逐 turn 建连、
  `thread/start` / `thread/resume` 会话复用（有界 `sessionKey → threadId` LRU）、
  agent 消息流式 delta、`turn/completed` 收尾。

它不是：

- OpenAI API 客户端（不直接调用 OpenAI API）。
- `codex` 二进制的替代品——本地路线必须安装并可运行 CLI；WebSocket 路线必须
  能连通 Codex app-server。

典型场景：

| 场景 | 使用内容 |
| :--- | :--- |
| 一次性编码任务 | `CodexClient.exec(prompt)` |
| 机器可读事件流 | `execAndParse(prompt)` → `List<CodexEvent>` |
| 长期运行的交互式智能体 | `startSession(prompt)` / `resumeSession(sessionId)` |
| 在沙箱中复现会话 | `forkSession(sessionId)` / `execResume(sessionId, prompt)` |
| 环境诊断 | `doctorSummary()` / `doctorJson()` |
| 远程智能体 + 会话连续性 | `CodexAppServerClient.runTurn(request)` + `sessionKey` |

## 2. 功能与状态

| 能力 | 状态 | 说明 |
| :--- | :--- | :--- |
| `codex exec` 非交互模式 | 活跃开发 | `exec`、`exec(model)`、`exec(ExecOptions)` |
| Exec 变体 | 活跃开发 | `execInDir`、`execEphemeral`、`execWithSearch`、`execToFile`、`execWithSchema`、`execWithImage`、`execWithConfigOverrides`、`execDangerously`、`execBypassHookTrust`、`execWithEnable` / `execWithDisable` |
| JSONL 事件解析 | 活跃开发 | `execAndParse(prompt)` → `List<CodexEvent>` |
| 交互式会话 | 活跃开发 | `startSession()`、`startSession(prompt)`、`startSession(GlobalOptions, prompt)` |
| 会话生命周期 | 活跃开发 | `resumeSession`、`resumeLastSession`、`forkSession`、`forkLastSession`、`archiveSession`、`unarchiveSession`、`execResume` |
| Doctor 与 review | 活跃开发 | `doctor`、`doctorJson`、`doctorSummary`、`review`、`reviewCommit`、`reviewBase` |
| 认证 / MCP / 其他 | 活跃开发 | `login`、`loginWithApiKey`、`loginWithAccessToken`、`loginDeviceAuth`、`loginStatus`、`logout`、`mcpList` / `mcpAdd` / `mcpGet` / `mcpRemove` / `mcpLogin` / `mcpLogout`、`update`、`features`、`completion`、`app` |
| 会话管理 | 活跃开发 | `archiveSession`、`unarchiveSession`、`queue`、`deleteSession`、`deleteSessionForce`、`agents`、`migrateRollouts` |
| App-server WebSocket 路线 | 活跃开发 | `CodexAppServerClient.runTurn` / `runTurnAsync`、`thread/start` / `thread/resume`、agent 消息 delta、`sessionKey → threadId` LRU（1000） |
| App-server 协议面 | 活跃开发 | `thread/list` / `read` / `fork` / `archive` / `unarchive` / `delete`、`turn/interrupt` / `turn/steer`、逃生通道 `execRpc`；`turnId` 经 `onTurnStarted` 与 `AppServerTurnResult` 暴露 |
| CLI typed 补齐 | 活跃开发 | `debugModels(Bundled)` / `debugPromptInput`、`mcpAddUrl(WithBearer)`、`pluginAdd/List/Remove` + `pluginMarketplace*`、`cloudExec` / `cloudList`、`featuresEnable/Disable/List`、`reviewPrompt` |
| 配置模型 | 活跃开发 | `CodexClientConfig` POJO（纯对象，可绑定 Spring 配置）、`CodexAppServerConfig` POJO |

> **注意**：上游已移除 `codex mcp-server` 子命令——`CodexClient.mcpServer()`
> 已标记废弃，请改用 `appServer(...)`。`ExecOptions` 额外支持
> `--ignore-rules` / `--ignore-user-config`；`GlobalOptions` 支持
> `--remote` / `--remote-auth-token-env`（连接远程 app-server 的 TUI 运行）。

> **假设**：以上能力状态反映当前活跃分支的情况；该模块处于活跃开发中。

## 3. 环境要求与兼容性

| 要求 | 版本 / 说明 |
| :--- | :--- |
| JDK | 21+ |
| Maven | 3.0+（enforcer 强制；项目内置 Maven Wrapper `./mvnw`） |
| Codex CLI | 本地路线：必须安装且可执行（`localExecutable` 可配置路径） |
| Codex app-server | 仅 WebSocket 路线：需要可达的 app-server（`baseUrl` 支持 ws/wss/http/https） |

> **注意**：app-server WebSocket 路线使用 JDK 内置 `java.net.http.HttpClient`
> （JDK 11+），仅在 `feature/2.0.x` 与 `feature/3.0.x` 版本线提供；
> `feature/1.0.x`（JDK 8）线仅包含 CLI 路线。

版本线：

| 分支 | JDK | 版本 |
| :--- | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

## 4. 架构与模块

```text
+------------------+   +---------------------------------------------+
| Java application |   | codex-java-sdk                               |
|                  |-->|  路线 1（本地）: CodexClient (facade)         |
| prompt / options |   |    | CodexCli (command mapping)             |
|                  |   |    |   | CodexCliExecutor                   |
|                  |   |    |   |   `codex` child process            |
|                  |   |    |   CodexCliResult                       |
|                  |   |  路线 2（远程）: CodexAppServerClient         |
|                  |   |    | JSON-RPC 2.0 over WebSocket            |
|                  |   |    | thread/start -> turn/start -> events   |
|                  |   | CodexEvent/CodexSession/CodexDoctorReport    |
+------------------+   +-------------------+-------------------------+
                                           |
                                           v
                     +-------------------------------------------+
                     | 本地 `codex` CLI（路线 1）或远程             |
                     | Codex app-server（路线 2）                  |
                     +-------------------------------------------+
```

单模块 Maven 工程（`packaging: jar`），无子模块。

| 构件 | 职责 |
| :--- | :--- |
| `io.github.easy4j:codex-java-sdk` | CLI 门面、命令映射、子进程执行器、WebSocket app-server 客户端、结果与解析模型 |

关键包：

| 包 | 内容 |
| :--- | :--- |
| `io.github.easy4j.codex` | `CodexClient`、`CodexClientConfig` |
| `io.github.easy4j.codex.cli` | `CodexCli`、`CodexCliExecutor`、`CodexCliResult` |
| `io.github.easy4j.codex.appserver` | `CodexAppServerClient`、`CodexAppServerConfig`、`AppServerTurnRequest`、`AppServerTurnResult`、`CodexAppServerListener`、`ThreadMappingStore`、`ThreadMappingCache`、`CodexAppServerException` |
| `io.github.easy4j.codex.model` | `CodexEvent`、`CodexSession`、`CodexDoctorReport` |

## 5. 安装

项目**尚未发布到 Maven Central**。快照 / 发布版本通过阿里云 Maven 仓库与 GitHub
Releases 分发。

Maven：

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>codex-java-sdk</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.github.easy4j:codex-java-sdk:3.0.x.x.20260630-SNAPSHOT'
```

## 6. 快速开始

```java
import io.github.easy4j.codex.CodexClient;
import io.github.easy4j.codex.CodexClientConfig;
import io.github.easy4j.codex.cli.CodexCliResult;

public class CodexDemo {

    public static void main(String[] args) {
        CodexClientConfig config = new CodexClientConfig();
        config.setLocalExecutable("codex");   // 或绝对路径
        config.setLocalTimeoutSeconds(600);

        try (CodexClient client = new CodexClient(config)) {
            CodexCliResult result = client.exec("Write a Java hello world");
            System.out.println("exit=" + result.getExitCode());
            System.out.println(result.getStdout());
        }
    }
}
```

预期结果：本地执行 `codex exec "Write a Java hello world"`；成功时
`result.getExitCode()` 为 `0`，`result.getStdout()` 包含智能体的回答。

## 7. 配置

`CodexClientConfig` 是纯 POJO（可绑定 Spring `@ConfigurationProperties`），
本身没有配置文件。关键字段：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `localExecutable` | String | `codex` | CLI 可执行文件名或绝对路径 |
| `localTimeoutSeconds` | int | `600` | 命令执行超时（秒） |
| `localProbeTimeoutSeconds` | int | `5` | CLI 可用性探测超时（秒） |
| `defaultModel` | String | - | 默认模型 |
| `defaultSandbox` | String | - | 沙箱模式（`read-only`、`workspace-write`、`danger-full-access`） |
| `defaultApprovalPolicy` | String | - | 审批策略（`untrusted`、`on-request`、`never`） |
| `defaultProfile` | String | - | 默认配置 profile |
| `ossProvider` / `localProvider` | boolean / String | - | OSS provider / 本地 provider（`lmstudio`、`ollama`） |
| `skipGitRepoCheck` | boolean | `false` | 跳过 git 仓库检查 |
| `ephemeral` | boolean | `false` | 临时会话（不持久化） |
| `jsonOutput` | boolean | `true` | JSONL 输出 |
| `outputSchema` | String | - | 输出 Schema 文件路径 |
| `search` | boolean | `false` | 启用 web 搜索 |
| `image` | String | - | 图片文件路径 |
| `configOverrides` | String[] | - | 配置覆盖（`-c key=value`） |
| `outputFile` | String | - | 输出文件路径（`output-last-message`） |
| `workingDir` | String | - | 工作目录 |
| `dangerouslyBypassApprovalsAndSandbox` | boolean | `false` | 跳过所有审批与沙箱（危险） |
| `dangerouslyBypassHookTrust` | boolean | `false` | 跳过 hook 信任检查 |
| `strictConfig` | boolean | `false` | 遇到未知配置字段即报错 |
| `enable` / `disable` | String[] | - | 启用 / 禁用的 feature |
| `noAltScreen` | boolean | `false` | 默认交互会话传递 `--no-alt-screen` |

运行时语义：
- `localProbeTimeoutSeconds` 只用于 CLI 可用性探测；普通命令继续使用 `localTimeoutSeconds`。
- `jsonOutput` 控制普通 `exec` 是否输出 JSON；`execAndParse` 因承诺解析 JSONL，会始终强制 `--json`。
- `noAltScreen` 会进入默认交互会话的全局参数。

### 7.1 `CodexAppServerConfig`（app-server WebSocket 路线）

> 仅 `feature/2.0.x`（JDK 17）与 `feature/3.0.x`（JDK 21）提供 App Server 路线；JDK 8 的 `feature/1.0.x` 明确保留为 CLI-only。

> **升级注意（3.0.x.x.20260630+）**：CLI 路线的参数改为原样传给子进程——
> 含空格的多词 prompt 不再被塞进字面双引号后发给 `codex`。CLI 非零退出现在
> 保留真实退出码与两路输出，不再折叠为 `exitCode=-1` 加空输出。通过明文
> `ws://` 携带 Bearer token 会打告警日志，生产环境请优先 `wss://`。

纯 POJO（可绑定 Spring `@ConfigurationProperties`）。字段名与常用的
`CodexEndpoint` 绑定保持一致：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `baseUrl` | String | - | app-server 基础地址（`ws://`/`wss://` 原样，`http://`/`https://` 自动升级） |
| `token` | String | - | 握手时以 `Authorization: Bearer <token>` 携带的凭证 |
| `connectTimeoutMillis` | int | `5000` | TCP/TLS + WebSocket 握手超时 |
| `readTimeoutMillis` | int | `120000` | 单个 turn 全程上限（建连 → `turn/completed`） |
| `maxSessionMappings` | int | `1000` | `sessionKey → threadId` LRU 上限；被淘汰的会话退化为新建线程 |
| `maxFrameChars` | int | `1048576` | 帧累积硬上限；超限的服务器帧使 turn 失败（`<= 0` = 不限） |
| `maxContentChars` | int | `1048576` | 单 turn agent 消息内容上限；超出部分截断并告警（`<= 0` = 不限） |

## 8. 核心用法 / API

### 8.1 JSONL 事件

```java
try (CodexClient client = new CodexClient(config)) {
    // codex exec --json <prompt>，解析为类型化事件
    List<CodexEvent> events = client.execAndParse("Fix the failing test");
    events.forEach(event -> System.out.println(event.getType() + " -> " + event.getMessage()));
}
```

### 8.2 会话生命周期

```java
try (CodexClient client = new CodexClient(config)) {
    client.exec("first task");                   // 创建持久化会话
    client.resumeSession("session-id");          // 恢复交互式会话
    client.forkSession("session-id");            // fork 出新会话
    client.archiveSession("session-id");         // 归档会话
    client.execResume("session-id", "continue"); // 非交互恢复
    client.doctorSummary();                      // 环境诊断
}
```

### 8.3 App-server WebSocket 路线（远程 Codex）

```java
import io.github.easy4j.codex.appserver.AppServerTurnRequest;
import io.github.easy4j.codex.appserver.AppServerTurnResult;
import io.github.easy4j.codex.appserver.CodexAppServerClient;
import io.github.easy4j.codex.appserver.CodexAppServerConfig;

CodexAppServerConfig config = new CodexAppServerConfig();
config.setBaseUrl("ws://codex-host:8081");   // http(s) 自动升级为 ws(s)
config.setToken("capability-token");
config.setReadTimeoutMillis(120_000);

try (CodexAppServerClient client = new CodexAppServerClient(config)) {
    AppServerTurnResult result = client.runTurn(AppServerTurnRequest.builder()
            .prompt("Fix the failing test")
            .sessionKey("chat-42")                                  // 启用 thread/resume 会话复用
            .onDelta(delta -> System.out.print(delta))              // agentMessage delta，按序回调
            .build());
    System.out.println(result.getThreadId() + " -> " + result.getContent());
}
```

### 8.4 App-server 协议操作

```java
try (CodexAppServerClient client = new CodexAppServerClient(config)) {
    List<AppServerThread> threads = client.listThreads(20);
    AppServerThread forked = client.forkThread("th_123");
    client.steerTurn("th_123", "turn_9", "也检查一下测试覆盖率");   // 运行中转向
    client.interruptTurn("th_123", "turn_9");                      // 运行中打断
    client.archiveThread("th_123");
    String raw = client.execRpc("model/list", Map.of("limit", 10)); // 逃生通道
}
```

当前每次 app-server 操作都使用请求级 WebSocket 短连接。每条连接都会先完成
`initialize` → `notifications/initialized`，再发送首个业务请求；同一连接上的
WebSocket 写入严格串行，避免协议帧发生越序。发送失败会立即终止当前操作，不再等到
后续读超时才暴露。

相同且非空的 `sessionKey` 会串行执行，不同 session 仍可并发。默认
`ThreadMappingCache` 是有界内存版 `ThreadMappingStore`；需要跨进程/重启持久化
时可以注入自定义 store 实现。

一个 turn 对应：`thread/start`（或 `thread/resume`）→ `turn/start` →
`item/agentMessage/delta` 实时文本流 → `turn/completed`。现有 `onDelta`
在服务器提供真实 delta 时会直接接收增量文本；对不提供 delta 的旧服务器，
`item/completed` 仍作为兼容回退，并避免重复追加已经流式输出的内容。新增的
`CodexAppServerListener` 可监听 turn start、文本 delta、item completed、token usage
和 warning。运行中的 turn id 可通过 `AppServerTurnRequest.onTurnStarted` /
listener 与 `AppServerTurnResult.getTurnId()` 获取。

未知通知只记录 debug 日志，不中断 turn。连接、JSON-RPC 错误、`turn/failed`、
`error`、WebSocket 发送失败、提前关闭或读超时统一以
`CodexAppServerException` 暴露。成功完成时优先保留服务器返回的 turn status；
服务器未提供时使用中性的 `completed`。

## 9. 测试与构建

```bash
./mvnw clean verify
```

- 构建配置了 JaCoCo Maven 插件（报告 + 绑定在 `verify` 阶段的 `check` 目标，
  行覆盖率规则为 90%；`haltOnFailure=false`）。
- 各维护分支均有完整测试套件；2.0.x / 3.0.x 还包含进程内假 app-server 的端到端 WebSocket 契约测试，以及默认关闭、按需启用的真实 app-server 集成测试。
- CI 工作流：`.github/workflows/ci.yml`。

## 10. 版本与分支

| 分支 | JDK | 版本 | 说明 |
| :--- | :--- | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` | CLI 兼容线；不提供 app-server transport |
| `feature/2.0.x` | 17 | `2.0.x.*` | App Server 协议/行为 canonical 版本线 |
| `feature/3.0.x` | 21 | `3.0.x.*` | JDK 21 / Maven 4 / Jackson 3 forward-port 版本线 |

维护策略：公共 CLI 修复保持三条版本线一致；App Server 协议行为先在
`feature/2.0.x` 验证，再 forward-port 到 `feature/3.0.x`；JDK 8 版本线明确保持
CLI-only。发布物通过阿里云 Maven 仓库与 GitHub
Releases 分发；项目尚未发布到 Maven Central。

## 11. 贡献与许可

欢迎通过 GitHub Issue 或 Pull Request 参与贡献。

本项目基于 [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) 许可。
