package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Container for "Java Threads:" or "Other Threads:" listing.
 */
public final class ThreadList implements ProcessSectionItem {

    private final @NotNull String header;
    private final @NotNull List<ThreadEntry> entries;
    private final @Nullable String totalLine;

    @JsonCreator
    public ThreadList(@JsonProperty("header") @NotNull String header,
                      @JsonProperty("entries") @NotNull List<ThreadEntry> entries,
                      @JsonProperty("totalLine") @Nullable String totalLine) {
        this.header = header;
        this.entries = List.copyOf(entries);
        this.totalLine = totalLine;
    }

    @JsonProperty public @NotNull String header() { return header; }
    @JsonProperty public @NotNull List<ThreadEntry> entries() { return entries; }
    @JsonProperty public @Nullable String totalLine() { return totalLine; }
    @JsonIgnore public boolean isJavaThreads() { return header.startsWith("Java Threads"); }

    public void accept(HsErrVisitor v) {
        if (isJavaThreads()) v.visitJavaThreadsList(this);
        else v.visitOtherThreadsList(this);
        for (ThreadEntry e : entries) e.accept(v);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (ThreadEntry e : entries) sb.append(e).append('\n');
        if (totalLine != null) sb.append(totalLine).append('\n');
        return sb.toString();
    }
}
