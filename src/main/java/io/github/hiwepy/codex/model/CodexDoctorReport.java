package io.github.hiwepy.codex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Codex doctor JSON 输出。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodexDoctorReport {

    private String version;
    private String platform;
    private String nodeVersion;
    private List<CheckItem> checks;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckItem {
        private String name;
        private String status;
        private String message;
        private boolean passed;
    }
}
