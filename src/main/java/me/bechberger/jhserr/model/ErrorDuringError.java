package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An "[error occurred during error reporting (...)]" marker.
 */
public final class ErrorDuringError implements ThreadSectionItem {

    private final @NotNull String line;
    private final @Nullable String step;
    private final @Nullable String id;
    private final @Nullable String detail;

    @JsonCreator
    public ErrorDuringError(@JsonProperty("line") @NotNull String line,
                            @JsonProperty("step") @Nullable String step,
                            @JsonProperty("id") @Nullable String id,
                            @JsonProperty("detail") @Nullable String detail) {
        this.line = line;
        this.step = step;
        this.id = id;
        this.detail = detail;
    }

    @JsonProperty public @NotNull String line() { return line; }
    @JsonProperty public @Nullable String step() { return step; }
    @JsonProperty public @Nullable String id() { return id; }
    @JsonProperty public @Nullable String detail() { return detail; }

    public void accept(HsErrVisitor v) { v.visitErrorDuringError(this); }

    @Override
    public String toString() { return line + "\n"; }
}
