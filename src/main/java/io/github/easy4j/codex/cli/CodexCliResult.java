package io.github.easy4j.codex.cli;

import lombok.Data;

/**
 * Codex CLI 执行结果。
 */
@Data
public class CodexCliResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public boolean isTimeout() {
        return exitCode == -1 && stderr != null && stderr.contains("timed out");
    }
}
