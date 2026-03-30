package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed VM state from a PROCESS section line.
 *
 * <p>Format: {@code VM state: not at safepoint (normal execution)}
 */
public record VmStateInfo(@NotNull String state, @Nullable String phase) implements ProcessSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "VM state:\\s*(.+?)(?:\\s+\\(([^)]+)\\))?$");

    @JsonCreator
    public VmStateInfo(@JsonProperty("state") @NotNull String state,
                       @JsonProperty("phase") @Nullable String phase) {
        this.state = state;
        this.phase = phase;
    }

    @Override public void accept(HsErrVisitor v) { v.visitVmStateInfo(this); }

    @Override
    public String toString() {
        return phase != null
                ? "VM state: %s (%s)\n".formatted(state, phase)
                : "VM state: %s\n".formatted(state);
    }

    public static @Nullable VmStateInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new VmStateInfo(m.group(1).strip(), m.group(2));
    }
}
