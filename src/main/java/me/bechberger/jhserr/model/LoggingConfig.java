package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.*;

/**
 * Logging configuration from the PROCESS section.
 *
 * <p>Header: {@code Logging:}
 */
public record LoggingConfig(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("entries") @NotNull List<Entry> entries
) implements ProcessSectionItem {

    private static final Pattern OUTPUT_PAT = Pattern.compile("^\\s+#(\\d+):\\s+(\\S+)\\s+(.+)$");

    @JsonCreator
    public LoggingConfig(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("entries") @Nullable List<Entry> entries) {
        this.name = Objects.requireNonNull(name, "name");
        this.entries = entries != null ? entries : List.of();
    }

    public static @NotNull LoggingConfig fromLines(@NotNull String name, @NotNull List<String> lines) {
        List<Entry> entries = new ArrayList<>(lines.size());
        for (String line : lines) {
            Matcher m = OUTPUT_PAT.matcher(line);
            if (m.matches()) {
                entries.add(new OutputEntry(
                        Integer.parseInt(m.group(1)),
                        m.group(2),
                        m.group(3)));
                continue;
            }
            entries.add(new RawLine(line));
        }
        return new LoggingConfig(name, entries);
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> out = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            out.add(entry.line());
        }
        return out;
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines()) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void accept(HsErrVisitor v) { v.visitLoggingConfig(this); }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RawLine.class, name = "raw"),
            @JsonSubTypes.Type(value = OutputEntry.class, name = "output")
    })
    public sealed interface Entry permits RawLine, OutputEntry {
        @JsonIgnore
        @NotNull String line();
    }

    public record RawLine(@JsonProperty("line") @NotNull String line) implements Entry {}

    /** A parsed JVM logging output configuration entry: {@code #N: target config}. */
    public record OutputEntry(
            @JsonProperty("index") int index,
            @JsonProperty("target") @NotNull String target,
            @JsonProperty("config") @NotNull String config
    ) implements Entry {
        @Override
        public @NotNull String line() {
            return " #" + index + ": " + target + " " + config;
        }
    }
}
