package me.bechberger.jhserr;

import me.bechberger.jhserr.model.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Root model for a parsed HotSpot error report (hs_err file).
 *
 * <p>Every section is fully parsed into typed fields. No raw line storage.
 * Use {@link #toString()} to reproduce the original hs_err text.
 */
public class HsErrReport {

    private final @Nullable Header header;
    private final @Nullable Summary summary;
    private final @Nullable ThreadSection thread;
    private final @Nullable ProcessSection process;
    private final @Nullable SystemSection system;

    /** True if "END." marker was present */
    private final boolean complete;

    /** Trailing blank line before END. (true if original file has blank line there) */
    private final @NotNull String endMarker;

    @JsonCreator
    public HsErrReport(@JsonProperty("header") @Nullable Header header,
                       @JsonProperty("summary") @Nullable Summary summary,
                       @JsonProperty("thread") @Nullable ThreadSection thread,
                       @JsonProperty("process") @Nullable ProcessSection process,
                       @JsonProperty("system") @Nullable SystemSection system,
                       @JsonProperty("endMarker") @NotNull String endMarker) {
        this.header = header;
        this.summary = summary;
        this.thread = thread;
        this.process = process;
        this.system = system;
        this.endMarker = endMarker;
        this.complete = endMarker.contains("END.");
    }

    @JsonProperty public @Nullable Header header() { return header; }
    @JsonProperty public @Nullable Summary summary() { return summary; }
    @JsonProperty public @Nullable ThreadSection thread() { return thread; }
    @JsonProperty public @Nullable ProcessSection process() { return process; }
    @JsonProperty public @Nullable SystemSection system() { return system; }
    @JsonProperty public @NotNull String endMarker() { return endMarker; }
    @JsonIgnore public boolean complete() { return complete; }

    public void accept(HsErrVisitor v) {
        v.visitReport(this);
        if (header != null) header.accept(v);
        if (summary != null) summary.accept(v);
        if (thread != null) thread.accept(v);
        if (process != null) process.accept(v);
        if (system != null) system.accept(v);
        v.visitEnd(complete);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        if (header != null) sb.append(header);
        if (summary != null) sb.append(summary);
        if (thread != null) sb.append(thread);
        if (process != null) sb.append(process);
        if (system != null) sb.append(system);
        sb.append(endMarker);
        return sb.toString();
    }
}
