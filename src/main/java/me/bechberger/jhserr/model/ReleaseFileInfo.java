package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.bechberger.jhserr.HsErrVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JDK release file content from the PROCESS section.
 *
 * <p>Header: {@code Release file:}
 */
public record ReleaseFileInfo(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("entries") @NotNull List<Entry> entries
) implements ProcessSectionItem {

    @JsonCreator
    public ReleaseFileInfo(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("entries") @Nullable List<Entry> entries) {
        this.name = name;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    /** Parser-compatible factory. */
    public static @NotNull ReleaseFileInfo fromLines(@NotNull String name, @NotNull List<String> lines) {
        return new ReleaseFileInfo(name, parseEntries(lines));
    }

    private static @NotNull List<Entry> parseEntries(@NotNull List<String> lines) {
        List<Entry> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                out.add(new Entry(line, false, false, ""));
                continue;
            }

            String key = line.substring(0, eq);
            String rhs = line.substring(eq + 1);
            boolean quoted = rhs.length() >= 2 && rhs.startsWith("\"") && rhs.endsWith("\"");
            String value = quoted ? rhs.substring(1, rhs.length() - 1) : rhs;
            out.add(new Entry(key, true, quoted, value));
        }
        return out;
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (!e.hasEquals()) {
                out.add(e.key());
                continue;
            }
            if (e.quoted()) {
                out.add(e.key() + "=\"" + e.value() + "\"");
            } else {
                out.add(e.key() + "=" + e.value());
            }
        }
        return out;
    }

    @Override
    public @NotNull String toString() {
        var sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines()) sb.append(line).append('\n');
        return sb.toString();
    }

    @Override
    public void accept(HsErrVisitor v) {
        v.visitReleaseFileInfo(this);
    }

    public record Entry(
            @JsonProperty("key") @NotNull String key,
            @JsonProperty("hasEquals") boolean hasEquals,
            @JsonProperty("quoted") boolean quoted,
            @JsonProperty("value") @NotNull String value
    ) {}
}
