package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import me.bechberger.jhserr.HsErrVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.*;

/**
 * Parsed "Top of Stack:" subsection from THREAD section.
 *
 * <p>Captures stack memory dump in typed {@link StackLine} entries
 * with address, hex bytes, and ASCII interpretation columns.
 *
 * <p>Example input:
 * <pre>
 * Top of Stack: (sp=0x000000016bef08c0)
 * 0x000000016bef08c0:   00007fff60ffcdb0 0000000000000000   ...`............
 * 0x000000016bef08d0:   00007fff92c1a4fc 00007fff939a7d00   .........}......
 * </pre>
 */
@JsonTypeName("TopOfStack")
public final class TopOfStack implements ThreadSectionItem {
    private final @NotNull String stackPointer;
    private final @NotNull List<StackLine> lines;

    /**
     * Single line from stack dump.
     */
    public record StackLine(
            @JsonProperty("address") @NotNull String address,
            @JsonProperty("hexBytes") @NotNull String hexBytes,
            @JsonProperty("asciiColumn") @NotNull String asciiColumn
    ) {
        /**
         * Reconstruct original line from parsed fields.
         */
        public String toString() {
            return address + ":   " + hexBytes + "   " + asciiColumn;
        }
    }

    @JsonCreator
    public TopOfStack(
            @JsonProperty("stackPointer") @NotNull String stackPointer,
            @JsonProperty("lines") @NotNull List<StackLine> lines
    ) {
        this.stackPointer = stackPointer;
        this.lines = lines;
    }

    /**
     * Create from raw lines (parser's responsibility).
     *
     * @param headerLine e.g. "Top of Stack: (sp=0x000000016bef08c0)"
     * @param bodyLines stack dump lines
     * @return parsed TopOfStack
     */
    public static @NotNull TopOfStack fromHeaderAndLines(
            @NotNull String headerLine,
            @NotNull List<String> bodyLines
    ) {
        // Extract stack pointer from header
        String stackPtr = extractStackPointer(headerLine);

        List<StackLine> parsedLines = new ArrayList<>();
        for (String line : bodyLines) {
            line = line.strip();
            if (line.isBlank()) continue;
            StackLine sl = parseStackLine(line);
            if (sl != null) {
                parsedLines.add(sl);
            }
        }

        return new TopOfStack(stackPtr, parsedLines);
    }

    private static final Pattern STACK_PTR_PATTERN = Pattern.compile("\\(sp=(0x[0-9a-f]+)\\)");
    private static final Pattern STACK_LINE_PATTERN = Pattern.compile("^(0x[0-9a-f]+):\\s{2,}([0-9a-f\\s]+?)\\s{2,}(.+)$", Pattern.CASE_INSENSITIVE);

    private static String extractStackPointer(String headerLine) {
        Matcher m = STACK_PTR_PATTERN.matcher(headerLine);
        return m.find() ? m.group(1) : "unknown";
    }

    private static StackLine parseStackLine(String line) {
        Matcher m = STACK_LINE_PATTERN.matcher(line);
        if (!m.find()) return null;

        return new StackLine(
                m.group(1),
                m.group(2).replaceAll("\\s+", " "),  // normalize spacing
                m.group(3)
        );
    }

    public @NotNull String stackPointer() {
        return stackPointer;
    }

    public @NotNull List<StackLine> lines() {
        return lines;
    }

    @Override
    public void accept(HsErrVisitor v) {
        v.visitTopOfStack(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Top of Stack: (sp=").append(stackPointer).append(")\n");
        for (StackLine line : lines) {
            sb.append(line.toString()).append("\n");
        }
        return sb.toString();
    }
}
