package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Active locale settings from the PROCESS section.
 *
 * <p>Header: {@code Active Locale:}
 */
public record ActiveLocale(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("entries") @NotNull List<LocaleEntry> entries
) implements ProcessSectionItem {

    @JsonCreator
    public ActiveLocale(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("entries") @Nullable List<LocaleEntry> entries) {
        this.name = Objects.requireNonNull(name, "name");
        this.entries = entries != null ? entries : List.of();
    }

    public static @NotNull ActiveLocale fromLines(@NotNull String name, @NotNull List<String> lines) {
        List<LocaleEntry> entries = new ArrayList<>(lines.size());
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                entries.add(new LocaleEntry(line.substring(0, eq), "=", line.substring(eq + 1)));
            } else {
                entries.add(new LocaleEntry(line, "", ""));
            }
        }
        return new ActiveLocale(name, entries);
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> out = new ArrayList<>(entries.size());
        for (LocaleEntry entry : entries) {
            out.add(entry.key() + entry.separator() + entry.value());
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
    public void accept(HsErrVisitor v) { v.visitActiveLocale(this); }

    public record LocaleEntry(
            @JsonProperty("key") @NotNull String key,
            @JsonProperty("separator") @NotNull String separator,
            @JsonProperty("value") @NotNull String value
    ) {}
}
