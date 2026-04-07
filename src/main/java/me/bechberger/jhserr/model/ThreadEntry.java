package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadEntry {

    private final boolean current;
    private final @Nullable String address;
    private final @Nullable String threadType;
    private final @Nullable String name;
    private final boolean daemon;
    private final @NotNull String spacingAfterName;
    private final @NotNull String spacingAfterDaemon;
    private final @Nullable String state;
    private final @Nullable String osThreadId;
    private final @Nullable String stackStart;
    private final @Nullable String stackEnd;
    private final @Nullable String stackSize;
    private final @Nullable String smrInfo;
    private final boolean oldBracketFormat;
    private final @Nullable String unparsedLine;

    @JsonCreator
    public ThreadEntry(@JsonProperty("current") boolean current,
                       @JsonProperty("address") @Nullable String address,
                       @JsonProperty("threadType") @Nullable String threadType,
                       @JsonProperty("name") @Nullable String name,
                       @JsonProperty("daemon") boolean daemon,
                       @JsonProperty("spacingAfterName") @NotNull String spacingAfterName,
                       @JsonProperty("spacingAfterDaemon") @NotNull String spacingAfterDaemon,
                       @JsonProperty("state") @Nullable String state,
                       @JsonProperty("osThreadId") @Nullable String osThreadId,
                       @JsonProperty("stackStart") @Nullable String stackStart,
                       @JsonProperty("stackEnd") @Nullable String stackEnd,
                       @JsonProperty("stackSize") @Nullable String stackSize,
                       @JsonProperty("smrInfo") @Nullable String smrInfo,
                       @JsonProperty("oldBracketFormat") boolean oldBracketFormat,
                       @JsonProperty("unparsedLine") @Nullable String unparsedLine) {
        this.current = current;
        this.address = address;
        this.threadType = threadType;
        this.name = name;
        this.daemon = daemon;
        this.spacingAfterName = spacingAfterName;
        this.spacingAfterDaemon = spacingAfterDaemon;
        this.state = state;
        this.osThreadId = osThreadId;
        this.stackStart = stackStart;
        this.stackEnd = stackEnd;
        this.stackSize = stackSize;
        this.smrInfo = smrInfo;
        this.oldBracketFormat = oldBracketFormat;
        this.unparsedLine = unparsedLine;
    }

    /** Fallback constructor for lines that couldn't be parsed. */
    public ThreadEntry(@NotNull String unparsedLine) {
        this(false, null, null, null, false, "", "", null, null, null, null, null, null, false, unparsedLine);
    }

    @JsonProperty public boolean current() { return current; }
    @JsonProperty public @Nullable String address() { return address; }
    @JsonProperty public @Nullable String threadType() { return threadType; }
    @JsonProperty public @Nullable String name() { return name; }
    @JsonProperty public boolean daemon() { return daemon; }
    @JsonProperty public @NotNull String spacingAfterName() { return spacingAfterName; }
    @JsonProperty public @NotNull String spacingAfterDaemon() { return spacingAfterDaemon; }
    @JsonProperty public @Nullable String state() { return state; }
    @JsonProperty public @Nullable String osThreadId() { return osThreadId; }
    @JsonProperty public @Nullable String stackStart() { return stackStart; }
    @JsonProperty public @Nullable String stackEnd() { return stackEnd; }
    @JsonProperty public @Nullable String stackSize() { return stackSize; }
    @JsonProperty public @Nullable String smrInfo() { return smrInfo; }
    @JsonProperty public boolean oldBracketFormat() { return oldBracketFormat; }
    @JsonProperty public @Nullable String unparsedLine() { return unparsedLine; }

    @JsonIgnore public boolean isJavaThread() { return "JavaThread".equals(threadType); }

    public void accept(HsErrVisitor v) { v.visitThreadEntry(this); }

    @Override
    public String toString() {
        if (unparsedLine != null) return unparsedLine;

        var sb = new StringBuilder();
        sb.append(current ? "=>" : "  ");
        sb.append(address).append(' ').append(threadType).append(" \"").append(name).append('"');
        sb.append(spacingAfterName);
        if (daemon) {
            sb.append("daemon").append(spacingAfterDaemon);
        }

        if (oldBracketFormat) {
            // Old format Other threads: [stack: START,END] [id=N]
            sb.append("[stack: ").append(stackStart).append(',').append(stackEnd).append(']');
            sb.append(" [id=").append(osThreadId).append(']');
        } else {
            // New bracket: [STATE, id=N, stack(START,END) (SIZEK)]
            sb.append('[');
            if (state != null) {
                sb.append(state).append(", ");
            }
            sb.append("id=").append(osThreadId);
            sb.append(", stack(").append(stackStart).append(',').append(stackEnd).append(')');
            if (stackSize != null) {
                sb.append(" (").append(stackSize).append(')');
            }
            sb.append(']');
        }

        if (smrInfo != null) {
            sb.append(' ').append(smrInfo);
        }

        return sb.toString();
    }
}
