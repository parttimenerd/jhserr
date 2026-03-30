package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * VM arguments from the PROCESS section.
 */
public final class VmArguments implements ProcessSectionItem {

    private final @NotNull String header;
    private final @NotNull List<Entry> entries;

    public record Entry(
            @NotNull String key,
            @NotNull String separator,
            @NotNull String value
    ) {
        @Override
        public @NotNull String toString() {
            return key + separator + value;
        }
    }

    @JsonCreator
    public VmArguments(@JsonProperty("header") @NotNull String header,
                       @JsonProperty("entries") @NotNull List<Entry> entries) {
        this.header = header;
        this.entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public static @NotNull VmArguments fromLines(@NotNull String header, @NotNull List<String> lines) {
        var parsed = new ArrayList<Entry>(lines.size());
        for (String line : lines) {
            parsed.add(parseEntry(line));
        }
        return new VmArguments(header, parsed);
    }

    @JsonProperty public @NotNull String header() { return header; }
    @JsonProperty public @NotNull List<Entry> entries() { return entries; }

    @JsonIgnore
    public @NotNull List<String> lines() {
        var lines = new ArrayList<String>(entries.size());
        for (Entry e : entries) lines.add(e.toString());
        return lines;
    }

    @JsonIgnore
    public @NotNull Map<String, String> valuesByKey() {
        var map = new LinkedHashMap<String, String>();
        for (Entry e : entries) map.put(e.key(), e.value());
        return Collections.unmodifiableMap(map);
    }

    @JsonIgnore public @Nullable String jvmArgs() {
        return findValue("jvm_args");
    }

    @JsonIgnore public @Nullable String jvmFlags() {
        return findValue("jvm_flags");
    }

    @JsonIgnore public @Nullable String javaCommand() {
        return findValue("java_command");
    }

    @JsonIgnore public @Nullable String classPath() {
        for (Entry e : entries) {
            if (e.key().startsWith("java_class_path")) return e.value();
        }
        return null;
    }

    @JsonIgnore public @Nullable String launcherType() {
        return findValue("Launcher Type");
    }

    public void accept(HsErrVisitor v) { v.visitVmArguments(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (Entry e : entries) sb.append(e).append('\n');
        return sb.toString();
    }

    private @Nullable String findValue(@NotNull String key) {
        for (Entry e : entries) {
            if (e.key().equals(key)) return e.value();
        }
        return null;
    }

    private static Entry parseEntry(@NotNull String line) {
        int idx = line.indexOf(':');
        if (idx < 0) {
            return new Entry(line, "", "");
        }
        String key = line.substring(0, idx);
        String separator = idx + 1 < line.length() && line.charAt(idx + 1) == ' ' ? ": " : ":";
        String value = line.substring(idx + separator.length());
        return new Entry(key, separator, value);
    }
}
