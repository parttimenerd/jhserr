package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An event log from the PROCESS section.
 */
public final class EventLog implements ProcessSectionItem {

    public record Entry(
            @NotNull String prefix,
            @Nullable String timestamp,
            @NotNull String separator,
            @NotNull String message
    ) {
        @JsonIgnore
        public boolean isStructuredEvent() {
            return timestamp != null;
        }

        public @NotNull String line() {
            if (timestamp == null) return message;
            return prefix + timestamp + separator + message;
        }
    }

    private final @NotNull String name;
    private final @NotNull String beforeCount;
    private final int declaredCount;
    private final @NotNull String betweenCountAndWord;
    private final @NotNull String eventWord;
    private final @NotNull String suffix;
    private final @NotNull List<Entry> entries;

    @JsonCreator
    public EventLog(@JsonProperty("name") @NotNull String name,
                    @JsonProperty("beforeCount") @NotNull String beforeCount,
                    @JsonProperty("declaredCount") int declaredCount,
                    @JsonProperty("betweenCountAndWord") @NotNull String betweenCountAndWord,
                    @JsonProperty("eventWord") @NotNull String eventWord,
                    @JsonProperty("suffix") @NotNull String suffix,
                    @JsonProperty("entries") @NotNull List<Entry> entries) {
        this.name = name;
        this.beforeCount = beforeCount;
        this.declaredCount = declaredCount;
        this.betweenCountAndWord = betweenCountAndWord;
        this.eventWord = eventWord;
        this.suffix = suffix;
        this.entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public EventLog(@NotNull String header, @NotNull List<String> events) {
        ParsedHeader parsedHeader = parseHeader(header);
        this.name = parsedHeader.name;
        this.beforeCount = parsedHeader.beforeCount;
        this.declaredCount = parsedHeader.declaredCount;
        this.betweenCountAndWord = parsedHeader.betweenCountAndWord;
        this.eventWord = parsedHeader.eventWord;
        this.suffix = parsedHeader.suffix;

        var parsedEntries = new ArrayList<Entry>(events.size());
        for (String line : events) parsedEntries.add(parseEntry(line));
        this.entries = List.copyOf(parsedEntries);
    }

    @JsonProperty public @NotNull String name() { return name; }
    @JsonProperty public @NotNull String beforeCount() { return beforeCount; }
    @JsonProperty public int declaredCount() { return declaredCount; }
    @JsonProperty public @NotNull String betweenCountAndWord() { return betweenCountAndWord; }
    @JsonProperty public @NotNull String eventWord() { return eventWord; }
    @JsonProperty public @NotNull String suffix() { return suffix; }
    @JsonProperty public @NotNull List<Entry> entries() { return entries; }

    @JsonIgnore
    public @NotNull String header() {
        if (declaredCount < 0) return name;
        return name + beforeCount + declaredCount + betweenCountAndWord + eventWord + suffix;
    }

    @JsonIgnore
    public @NotNull List<String> events() {
        var lines = new ArrayList<String>(entries.size());
        for (Entry e : entries) lines.add(e.line());
        return lines;
    }

    public void accept(HsErrVisitor v) { v.visitEventLog(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header()).append('\n');
        for (Entry e : entries) sb.append(e.line()).append('\n');
        return sb.toString();
    }

    private record ParsedHeader(
            String name,
            String beforeCount,
            int declaredCount,
            String betweenCountAndWord,
            String eventWord,
            String suffix
    ) {}

    private static final Pattern HEADER_PAT = Pattern.compile("^(.*?)(\\s*\\()(\\d+)(\\s+)(events?)(\\):)$");
    private static final Pattern EVENT_PAT = Pattern.compile("^(Event:\\s*)(\\d+(?:\\.\\d+)?)(\\s+)(.*)$");

    private static ParsedHeader parseHeader(@NotNull String header) {
        var m = HEADER_PAT.matcher(header);
        if (!m.find()) {
            return new ParsedHeader(header, "", -1, "", "", "");
        }
        return new ParsedHeader(
                m.group(1),
                m.group(2),
                Integer.parseInt(m.group(3)),
                m.group(4),
                m.group(5),
                m.group(6));
    }

    private static Entry parseEntry(@NotNull String line) {
        var m = EVENT_PAT.matcher(line);
        if (m.find()) {
            return new Entry(m.group(1), m.group(2), m.group(3), m.group(4));
        }
        return new Entry("", null, "", line);
    }
}
