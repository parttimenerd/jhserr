package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed compilation status from the PROCESS section.
 *
 * <p>Format: {@code Compilation: enabled, stopped_count=0, restarted_count=0}
 */
public record CompilationInfo(boolean enabled, int stoppedCount, int restartedCount) implements ProcessSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "Compilation:\\s*(\\w+),\\s*stopped_count=(\\d+),\\s*restarted_count=(\\d+)");

    @JsonCreator
    public CompilationInfo(@JsonProperty("enabled") boolean enabled,
                          @JsonProperty("stoppedCount") int stoppedCount,
                          @JsonProperty("restartedCount") int restartedCount) {
        this.enabled = enabled;
        this.stoppedCount = stoppedCount;
        this.restartedCount = restartedCount;
    }

    @Override public void accept(HsErrVisitor v) { v.visitCompilationInfo(this); }

    @Override
    public String toString() {
        return "Compilation: %s, stopped_count=%d, restarted_count=%d\n"
                .formatted(enabled ? "enabled" : "disabled", stoppedCount, restartedCount);
    }

    public static @Nullable CompilationInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new CompilationInfo(
                "enabled".equals(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)));
    }
}
