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

/**
 * Signal handler configuration from the PROCESS section.
 *
 * <p>Header: {@code Signal Handlers:}
 */
public record SignalHandlers(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("entries") @NotNull List<Entry> entries
) implements ProcessSectionItem {

    @JsonCreator
    public SignalHandlers(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("entries") @Nullable List<Entry> entries) {
        this.name = Objects.requireNonNull(name, "name");
        this.entries = entries != null ? entries : List.of();
    }

    public static @NotNull SignalHandlers fromLines(@NotNull String name, @NotNull List<String> lines) {
        List<Entry> entries = new ArrayList<>(lines.size());
        for (String line : lines) {
            int firstNonWs = 0;
            while (firstNonWs < line.length() && Character.isWhitespace(line.charAt(firstNonWs))) {
                firstNonWs++;
            }
            int colon = line.indexOf(':', firstNonWs);
            if (firstNonWs < colon) {
                String signalName = line.substring(firstNonWs, colon);
                if (signalName.startsWith("SIG") && signalName.indexOf(' ') < 0) {
                    entries.add(new SignalHandlerEntry(
                            line.substring(0, firstNonWs),
                            signalName,
                            line.substring(colon, colon + 1),
                            line.substring(colon + 1)
                    ));
                    continue;
                }
            }
            entries.add(new RawLine(line));
        }
        return new SignalHandlers(name, entries);
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
    public void accept(HsErrVisitor v) { v.visitSignalHandlers(this); }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RawLine.class, name = "raw"),
            @JsonSubTypes.Type(value = SignalHandlerEntry.class, name = "signal-handler")
    })
    public sealed interface Entry permits RawLine, SignalHandlerEntry {
        @JsonIgnore
        @NotNull String line();
    }

    public record RawLine(@JsonProperty("line") @NotNull String line) implements Entry {}

    public record SignalHandlerEntry(
            @JsonProperty("leadingWhitespace") @NotNull String leadingWhitespace,
            @JsonProperty("signalName") @NotNull String signalName,
            @JsonProperty("separator") @NotNull String separator,
            @JsonProperty("details") @NotNull String details
    ) implements Entry {
        @Override
        public @NotNull String line() {
            return leadingWhitespace + signalName + separator + details;
        }
    }
}
