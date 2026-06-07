package io.github.hiwepy.codex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Codex exec --json 输出中的单条事件。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodexEvent {

    private String type;
    private String message;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("session_id")
    private String sessionId;

    private Object data;
    private Object error;
}
