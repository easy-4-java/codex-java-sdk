# codex-java-sdk

[English](./README.md) | [简体中文](./README.zh-CN.md)

[![Java](https://img.shields.io/badge/Java-8-orange)](https://github.com/easy-4-java/codex-java-sdk) [![License](https://img.shields.io/badge/license-Apache%202.0-green)](https://www.apache.org/licenses/LICENSE-2.0.txt)

> [Codex CLI](https://github.com/openai/codex) 的 Java SDK：通过子进程集成驱动本地
> `codex` 智能体（exec 非交互执行、交互会话、会话 resume / fork / archive、
> doctor、review）。

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

`codex-java-sdk` 让 Java 应用把 [Codex CLI](https://github.com/openai/codex) 智能体
（`codex`）作为本地子进程运行。它是 **CLI 封装**，不是直连 OpenAI API 客户端——
每次调用都对应一次真实的 `codex` 命令行执行。

SDK 覆盖：

- **Exec 模式** — `codex exec <prompt>`，支持模型、沙箱、JSONL 输出、web 搜索、
  输出文件、输出 Schema、图片、配置覆盖与临时运行。
- **交互式会话** — `codex [prompt]` 与完整会话生命周期：`resume` / `resumeLast` /
  `fork` / `archive` / `unarchive`。
- **解析模型** — `CodexEvent`（JSONL 事件）、`CodexSession`、`CodexDoctorReport`。
- **工具类** — `doctor`、`review`、`login` / `logout`、MCP 管理、`update`、
  `features`、shell `completion`。

它不是：

- OpenAI API 客户端（不直接调用 OpenAI API）。
- `codex` 二进制的替代品——必须安装并可直接运行的 CLI。

典型场景：

| 场景 | 使用内容 |
| :--- | :--- |
| 一次性编码任务 | `CodexClient.exec(prompt)` |
| 机器可读事件流 | `execAndParse(prompt)` → `List<CodexEvent>` |
| 长期运行的交互式智能体 | `startSession(prompt)` / `resumeSession(sessionId)` |
| 在沙箱中复现会话 | `forkSession(sessionId)` / `execResume(sessionId, prompt)` |
| 环境诊断 | `doctorSummary()` / `doctorJson()` |

## 2. 功能与状态

| 能力 | 状态 | 说明 |
| :--- | :--- | :--- |
| `codex exec` 非交互模式 | 活跃开发 | `exec`、`exec(model)`、`exec(ExecOptions)` |
| Exec 变体 | 活跃开发 | `execInDir`、`execEphemeral`、`execWithSearch`、`execToFile`、`execWithSchema`、`execWithImage`、`execWithConfigOverrides`、`execDangerously`、`execBypassHookTrust`、`execWithEnable` / `execWithDisable` |
| JSONL 事件解析 | 活跃开发 | `execAndParse(prompt)` → `List<CodexEvent>` |
| 交互式会话 | 活跃开发 | `startSession()`、`startSession(prompt)`、`startSession(GlobalOptions, prompt)` |
| 会话生命周期 | 活跃开发 | `resumeSession`、`resumeLastSession`、`forkSession`、`forkLastSession`、`archiveSession`、`unarchiveSession`、`execResume` |
| Doctor 与 review | 活跃开发 | `doctor`、`doctorJson`、`doctorSummary`、`review`、`reviewCommit`、`reviewBase` |
| 认证 / MCP / 其他 | 活跃开发 | `login`、`logout`、`mcpList` / `mcpAdd` / `mcpGet` / `mcpRemove`、`update`、`features`、`completion`、`app` |
| 配置模型 | 活跃开发 | `CodexClientConfig` POJO（纯对象，可绑定 Spring 配置） |

> **假设**：以上能力状态反映 1.0.x 分支当前情况；该模块处于活跃开发中。

## 3. 环境要求与兼容性

| 要求 | 版本 / 说明 |
| :--- | :--- |
| JDK | 8+ |
| Maven | 3.0+（enforcer 强制；项目内置 Maven Wrapper `./mvnw`） |
| Codex CLI | 必须安装且可执行（`localExecutable` 可配置路径） |

版本线：

| 分支 | JDK | 版本 |
| :--- | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

## 4. 架构与模块

```text
+------------------+   +------------------------------------------+
| Java application |   | codex-java-sdk                            |
|                  |-->|  CodexClient (facade)                    |
| prompt / options |   |    | CodexCli (command mapping)          |
|                  |   |    |   | CodexCliExecutor                |
|                  |   |    |   |   `codex` child process         |
|                  |   |    |   CodexCliResult                    |
+------------------+   |    | CodexEvent/CodexSession/DoctorReport|
                       +-------------------+----------------------+
                                           |
                                           v
                     +-------------------------------------------+
                     | Local `codex` CLI (exec, session, doctor, |
                     | review, login, ...)                       |
                     +-------------------------------------------+
```

单模块 Maven 工程（`packaging: jar`），无子模块。

| 构件 | 职责 |
| :--- | :--- |
| `io.github.easy4j:codex-java-sdk` | CLI 门面、命令映射、子进程执行器、结果与解析模型 |

关键包：

| 包 | 内容 |
| :--- | :--- |
| `io.github.easy4j.codex` | `CodexClient`、`CodexClientConfig` |
| `io.github.easy4j.codex.cli` | `CodexCli`、`CodexCliExecutor`、`CodexCliResult` |
| `io.github.easy4j.codex.model` | `CodexEvent`、`CodexSession`、`CodexDoctorReport` |

## 5. 安装

项目**尚未发布到 Maven Central**。快照 / 发布版本通过阿里云 Maven 仓库与 GitHub
Releases 分发。

Maven：

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>codex-java-sdk</artifactId>
    <version>1.0.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.github.easy4j:codex-java-sdk:1.0.x.20260630-SNAPSHOT'
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

## 9. 测试与构建

```bash
./mvnw clean verify
```

- 构建配置了 JaCoCo Maven 插件（报告 + 绑定在 `verify` 阶段的 `check` 目标，
  行覆盖率规则为 90%；`haltOnFailure=false`）。
- **假设**：1.0.x 分支当前 `src/test` 下未提交测试源码；覆盖率门禁仅在存在测试时生效。
- 本 worktree 的 `.github/` 下无 CI 工作流文件。

## 10. 版本与分支

| 分支 | JDK | 版本 | 说明 |
| :--- | :--- | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` | 当前分支，JDK 8 基线，活跃开发 |
| `feature/2.0.x` | 17 | `2.0.x.*` | JDK 17 版本线 |
| `feature/3.0.x` | 21 | `3.0.x.*` | JDK 21 版本线 |

维护策略：`1.0.x` 版本线接收针对 JDK 8 基线的缺陷修复与兼容性更新；面向新 JDK 的
新特性在 `2.0.x` / `3.0.x` 版本线开发。发布物通过阿里云 Maven 仓库与 GitHub
Releases 分发；项目尚未发布到 Maven Central。

## 11. 贡献与许可

欢迎通过 GitHub Issue 或 Pull Request 参与贡献。

本项目基于 [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) 许可。
