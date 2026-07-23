package io.github.easy4j.codex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Codex 会话信息（从 JSON 输出解析）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodexSession {

    private String id;
    private String name;
    private String cwd;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("is_archived")
    private boolean archived;

    @JsonProperty("is_interactive")
    private boolean interactive;

    @JsonProperty("model")
    private String model;
}
