package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JVM global flags from the PROCESS section.
 *
 * <p>Header: {@code [Global flags]}
 */
public final class GlobalFlags implements ProcessSectionItem {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = FlagEntry.class, name = "flag"),
            @JsonSubTypes.Type(value = ContinuationLine.class, name = "continuation"),
            @JsonSubTypes.Type(value = RawLine.class, name = "raw")
    })
    public sealed interface Line permits FlagEntry, ContinuationLine, RawLine {
        @NotNull String asText();
    }

    public record FlagEntry(
            @NotNull String leadingWhitespace,
            @NotNull String type,
            @NotNull String betweenTypeAndName,
            @NotNull String name,
            @NotNull String betweenNameAndEquals,
            @NotNull String betweenEqualsAndValue,
            @NotNull String value,
            @NotNull String betweenValueAndAttributes,
            @NotNull String attributesSegment
    ) implements Line {
        @Override
        public @NotNull String asText() {
            return leadingWhitespace + type + betweenTypeAndName + name
                    + betweenNameAndEquals + "=" + betweenEqualsAndValue + value
                    + betweenValueAndAttributes + attributesSegment;
        }

        @JsonIgnore
        public @NotNull List<String> attributes() {
            var attrs = new ArrayList<String>();
            Matcher m = ATTR_PAT.matcher(attributesSegment);
            while (m.find()) attrs.add(m.group());
            return attrs;
        }
    }

    public record ContinuationLine(@NotNull String indentation,
                                   @NotNull String keyword,
                                   @NotNull String separator,
                                   @NotNull String content) implements Line {
        @Override
        public @NotNull String asText() {
            return indentation + keyword + separator + content;
        }
    }

    public record RawLine(@NotNull String text) implements Line {
        @Override
        public @NotNull String asText() {
            return text;
        }
    }

    private static final Pattern MAIN_PAT = Pattern.compile("^(\\s*)(\\S+)(\\s+)(\\S+)(\\s*)=(\\s*)(.*)$");
    private static final Pattern ATTR_PAT = Pattern.compile("\\{[^}]*}");
    private static final Pattern CONT_PAT = Pattern.compile("^(\\s*)(ccstrlist)(\\s+)(.*)$");

    private final @NotNull String header;
    private final @NotNull List<Line> lines;

    @JsonCreator
    public GlobalFlags(@JsonProperty("header") @NotNull String header,
                       @JsonProperty("lines") @NotNull List<Line> lines) {
        this.header = header;
        this.lines = lines != null ? List.copyOf(lines) : List.of();
    }

    public static @NotNull GlobalFlags fromLines(@NotNull String header, @NotNull List<String> bodyLines) {
        var parsed = new ArrayList<Line>(bodyLines.size());
        for (String line : bodyLines) parsed.add(parseLine(line));
        return new GlobalFlags(header, parsed);
    }

    @JsonProperty public @NotNull String header() { return header; }
    @JsonProperty public @NotNull List<Line> lines() { return lines; }

    @JsonIgnore
    public @NotNull List<FlagEntry> entries() {
        var entries = new ArrayList<FlagEntry>();
        for (Line line : lines) {
            if (line instanceof FlagEntry e) entries.add(e);
        }
        return entries;
    }

    @JsonIgnore
    public @NotNull List<String> bodyLines() {
        var body = new ArrayList<String>(lines.size());
        for (Line line : lines) body.add(line.asText());
        return body;
    }

    @Override
    public void accept(HsErrVisitor v) { v.visitGlobalFlags(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (Line line : lines) sb.append(line.asText()).append('\n');
        return sb.toString();
    }

    private static Line parseLine(@NotNull String line) {
        Matcher cm = CONT_PAT.matcher(line);
        if (cm.find()) {
            return new ContinuationLine(cm.group(1), cm.group(2), cm.group(3), cm.group(4));
        }

        int firstAttr = firstAttributeIndex(line);
        String main = firstAttr >= 0 ? line.substring(0, firstAttr) : line;
        String attrSegment = firstAttr >= 0 ? line.substring(firstAttr) : "";

        Matcher m = MAIN_PAT.matcher(main);
        if (!m.find()) {
            return new RawLine(line);
        }

        String valueAndPadding = m.group(7);
        int valueEnd = valueAndPadding.length();
        while (valueEnd > 0 && Character.isWhitespace(valueAndPadding.charAt(valueEnd - 1))) valueEnd--;
        String value = valueAndPadding.substring(0, valueEnd);
        String betweenValueAndAttributes = valueAndPadding.substring(valueEnd);

        return new FlagEntry(
                m.group(1),
                m.group(2),
                m.group(3),
                m.group(4),
                m.group(5),
                m.group(6),
                value,
                betweenValueAndAttributes,
                attrSegment);
    }

    private static int firstAttributeIndex(@NotNull String line) {
        Matcher m = ATTR_PAT.matcher(line);
        return m.find() ? m.start() : -1;
    }
}
