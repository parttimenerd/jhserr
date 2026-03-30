package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadEntry {

    private final @NotNull String line;
    private final boolean current;
    private final @Nullable String address;
    private final @Nullable String threadType;
    private final @Nullable String name;
    private final boolean daemon;
    private final @Nullable String state;
    private final @Nullable String osThreadId;
    private final @Nullable String stackStart;
    private final @Nullable String stackEnd;
    private final @Nullable String stackSize;
    private final @Nullable String smrInfo;

    @JsonCreator
    public ThreadEntry(@JsonProperty("line") @NotNull String line,
                       @JsonProperty("current") boolean current,
                       @JsonProperty("address") @Nullable String address,
                       @JsonProperty("threadType") @Nullable String threadType,
                       @JsonProperty("name") @Nullable String name,
                       @JsonProperty("daemon") boolean daemon,
                       @JsonProperty("state") @Nullable String state,
                       @JsonProperty("osThreadId") @Nullable String osThreadId,
                       @JsonProperty("stackStart") @Nullable String stackStart,
                       @JsonProperty("stackEnd") @Nullable String stackEnd,
                       @JsonProperty("stackSize") @Nullable String stackSize,
                       @JsonProperty("smrInfo") @Nullable String smrInfo) {
        this.line = line;
        this.current = current;
        this.address = address;
        this.threadType = threadType;
        this.name = name;
        this.daemon = daemon;
        this.state = state;
        this.osThreadId = osThreadId;
        this.stackStart = stackStart;
        this.stackEnd = stackEnd;
        this.stackSize = stackSize;
        this.smrInfo = smrInfo;
    }

    public ThreadEntry(@NotNull String line, boolean current, @Nullable String address,
                       @Nullable String threadType, @Nullable String name, boolean daemon) {
        this(line, current, address, threadType, name, daemon, null, null, null, null, null, null);
    }

    @JsonProperty public @NotNull String line() { return line; }
    @JsonProperty public boolean current() { return current; }
    @JsonProperty public @Nullable String address() { return address; }
    @JsonProperty public @Nullable String threadType() { return threadType; }
    @JsonProperty public @Nullable String name() { return name; }
    @JsonProperty public boolean daemon() { return daemon; }
    @JsonProperty public @Nullable String state() { return state; }
    @JsonProperty public @Nullable String osThreadId() { return osThreadId; }
    @JsonProperty public @Nullable String stackStart() { return stackStart; }
    @JsonProperty public @Nullable String stackEnd() { return stackEnd; }
    @JsonProperty public @Nullable String stackSize() { return stackSize; }
    @JsonProperty public @Nullable String smrInfo() { return smrInfo; }

    @JsonIgnore public boolean isJavaThread() { return "JavaThread".equals(threadType); }

    public void accept(HsErrVisitor v) { v.visitThreadEntry(this); }

    @Override
    public String toString() { return line; }
}
