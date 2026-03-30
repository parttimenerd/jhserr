package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A named list of stack frames ("Native frames" or "Java frames").
 */
public final class FrameList implements ThreadSectionItem {

    private final @NotNull String headerLine;
    private final @NotNull List<StackFrame> frames;

    @JsonCreator
    public FrameList(@JsonProperty("headerLine") @NotNull String headerLine,
                     @JsonProperty("frames") @NotNull List<StackFrame> frames) {
        this.headerLine = headerLine;
        this.frames = frames != null ? List.copyOf(frames) : List.of();
    }

    @JsonProperty public @NotNull String headerLine() { return headerLine; }
    @JsonProperty public @NotNull List<StackFrame> frames() { return frames; }

    public void accept(HsErrVisitor v) {
        if (headerLine.startsWith("Native")) v.visitNativeFrames(this);
        else v.visitJavaFrames(this);
        for (StackFrame f : frames) f.accept(v);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(headerLine).append('\n');
        for (StackFrame f : frames) sb.append(f).append('\n');
        return sb.toString();
    }
}
