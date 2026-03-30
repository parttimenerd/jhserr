package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.bechberger.jhserr.HsErrVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed CodeCache section with individual heap entries.
 * Replaces generic NamedSection for "CodeCache:" sections.
 *
 * Contains multiple CodeHeapEntry records plus aggregate totals.
 */
public record CodeCacheInfo(
        @JsonProperty("header") @NotNull String header,
        @JsonProperty("entries") @NotNull List<CodeHeapEntry> entries,
        @JsonProperty("totalSizeKb") long totalSizeKb,
        @JsonProperty("totalUsedKb") long totalUsedKb,
        @JsonProperty("totalMaxUsedKb") long totalMaxUsedKb,
    @JsonProperty("totalFreeKb") long totalFreeKb,
    @JsonProperty("totalBlobs") @Nullable Integer totalBlobs,
    @JsonProperty("nmethods") @Nullable Integer nmethods,
    @JsonProperty("adapters") @Nullable Integer adapters,
    @JsonProperty("fullCount") @Nullable Integer fullCount,
    @JsonProperty("compilationEnabled") @Nullable Boolean compilationEnabled,
    @JsonProperty("stoppedCount") @Nullable Integer stoppedCount,
    @JsonProperty("restartedCount") @Nullable Integer restartedCount,
    @JsonProperty("trailingStoppedCount") @Nullable Integer trailingStoppedCount,
    @JsonProperty("trailingRestartedCount") @Nullable Integer trailingRestartedCount,
    @JsonProperty("trailingFullCount") @Nullable Integer trailingFullCount,
    @JsonProperty("unparsedLines") @NotNull List<String> unparsedLines,
    @JsonProperty("originalLines") @NotNull List<String> originalLines,
    @JsonProperty("parseFailedCompletely") boolean parseFailedCompletely
) implements ProcessSectionItem {

    private static final Pattern CODE_HEAP_LINE = Pattern.compile(
        "^CodeHeap '([^']+)': size=(\\d+)Kb used=(\\d+)Kb max_used=(\\d+)Kb free=(\\d+)Kb$");
    private static final Pattern BOUNDS_LINE = Pattern.compile(
        "^\\s*bounds \\[(0x[0-9a-fA-F]+),\\s*(0x[0-9a-fA-F]+),\\s*(0x[0-9a-fA-F]+)]$");
    private static final Pattern TOTAL_LINE = Pattern.compile(
        "^CodeCache: size=(\\d+)Kb, used=(\\d+)Kb, max_used=(\\d+)Kb, free=(\\d+)Kb$");
    private static final Pattern TOTAL_BLOBS_LINE = Pattern.compile(
        "^ total_blobs=(\\d+)(?:, nmethods=(\\d+), adapters=(\\d+), full_count=(\\d+)| nmethods=(\\d+) adapters=(\\d+))\\s*$");
    private static final Pattern COMPILATION_LINE = Pattern.compile(
        "^ compilation: (enabled|disabled)\\s*$");
    private static final Pattern COMPILATION_EXT_LINE = Pattern.compile(
        "^Compilation: (enabled|disabled), stopped_count=(\\d+), restarted_count=(\\d+)\\s*$");
    private static final Pattern STOPPED_RESTARTED_LINE = Pattern.compile(
        "^\\s+stopped_count=(\\d+), restarted_count=(\\d+)\\s*$");
    private static final Pattern FULL_COUNT_LINE = Pattern.compile(
        "^\\s+full_count=(\\d+)\\s*$");
    // Match "Compilation events (...)" or similar event log headers - capture but don't consume
    private static final Pattern EVENT_LOG_HEADER = Pattern.compile(
        "^.*(Compilation|DeoptCommand|GC Precious|Event Log|GC|Dll|Deopt) .*\\(\\d+ events?\\):.*$");

    @JsonCreator
    public CodeCacheInfo(
            @JsonProperty("header") @NotNull String header,
            @JsonProperty("entries") @Nullable List<CodeHeapEntry> entries,
            @JsonProperty("totalSizeKb") long totalSizeKb,
            @JsonProperty("totalUsedKb") long totalUsedKb,
            @JsonProperty("totalMaxUsedKb") long totalMaxUsedKb,
            @JsonProperty("totalFreeKb") long totalFreeKb,
            @JsonProperty("totalBlobs") @Nullable Integer totalBlobs,
            @JsonProperty("nmethods") @Nullable Integer nmethods,
            @JsonProperty("adapters") @Nullable Integer adapters,
            @JsonProperty("fullCount") @Nullable Integer fullCount,
            @JsonProperty("compilationEnabled") @Nullable Boolean compilationEnabled,
            @JsonProperty("stoppedCount") @Nullable Integer stoppedCount,
            @JsonProperty("restartedCount") @Nullable Integer restartedCount,
            @JsonProperty("trailingStoppedCount") @Nullable Integer trailingStoppedCount,
            @JsonProperty("trailingRestartedCount") @Nullable Integer trailingRestartedCount,
            @JsonProperty("trailingFullCount") @Nullable Integer trailingFullCount,
            @JsonProperty("unparsedLines") @Nullable List<String> unparsedLines,
            @JsonProperty("originalLines") @Nullable List<String> originalLines,
            @JsonProperty("parseFailedCompletely") boolean parseFailedCompletely) {
        this.header = header;
        this.entries = entries != null ? entries : new ArrayList<>();
        this.totalSizeKb = totalSizeKb;
        this.totalUsedKb = totalUsedKb;
        this.totalMaxUsedKb = totalMaxUsedKb;
        this.totalFreeKb = totalFreeKb;
        this.totalBlobs = totalBlobs;
        this.nmethods = nmethods;
        this.adapters = adapters;
        this.fullCount = fullCount;
        this.compilationEnabled = compilationEnabled;
        this.stoppedCount = stoppedCount;
        this.restartedCount = restartedCount;
        this.trailingStoppedCount = trailingStoppedCount;
        this.trailingRestartedCount = trailingRestartedCount;
        this.trailingFullCount = trailingFullCount;
        this.unparsedLines = unparsedLines != null ? unparsedLines : new ArrayList<>();
        this.originalLines = originalLines != null ? originalLines : new ArrayList<>();
        this.parseFailedCompletely = parseFailedCompletely;
    }

    /**
     * Parser-compatible constructor used by {@link me.bechberger.jhserr.parser.HsErrParser}.
     */
    public CodeCacheInfo(@NotNull String header, @NotNull List<String> lines) {
    this(parse(header, lines));
    }

    private CodeCacheInfo(@NotNull Parsed parsed) {
        this(parsed.header, parsed.entries, parsed.totalSizeKb, parsed.totalUsedKb,
                parsed.totalMaxUsedKb, parsed.totalFreeKb,
                parsed.totalBlobs, parsed.nmethods, parsed.adapters, parsed.fullCount,
                parsed.compilationEnabled, parsed.stoppedCount, parsed.restartedCount,
                parsed.trailingStoppedCount, parsed.trailingRestartedCount, parsed.trailingFullCount,
                parsed.unparsedLines, parsed.originalLines, parsed.parseFailedCompletely);
    }

    @Override
    @JsonIgnore
    public void accept(HsErrVisitor v) {
        v.visitCodeCacheInfo(this);
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        // If parsing failed completely, just return the original lines for perfect round-trip
        if (parseFailedCompletely) {
            return new ArrayList<>(originalLines);
        }
        
        List<String> lines = new ArrayList<>();
        boolean headerIsHeapLine = header.startsWith("CodeHeap '");
        boolean headerIsCodeCacheLine = header.startsWith("CodeCache:");

        // For lenient parsing: output in the order they appeared
        // First, output any parsed CodeHeap entries
        for (int idx = 0; idx < entries.size(); idx++) {
            CodeHeapEntry entry = entries.get(idx);
            boolean sameAsSectionHeader = idx == 0 && headerIsHeapLine && header.equals(entry.toHeaderLine());
            if (!sameAsSectionHeader) {
                lines.add(entry.toHeaderLine());
            }
            lines.add(entry.toBoundsLine());
        }
        
        // Then output aggregate totals if present
        if (totalSizeKb > 0 || totalUsedKb > 0 || totalMaxUsedKb > 0 || totalFreeKb > 0) {
            String totalLine = String.format("CodeCache: size=%dKb, used=%dKb, max_used=%dKb, free=%dKb",
                    totalSizeKb, totalUsedKb, totalMaxUsedKb, totalFreeKb);
            if (!(headerIsCodeCacheLine && header.equals(totalLine))) {
                lines.add(totalLine);
            }
        }
        
        // Then output blobs/compilation info in the  order they appeared
        if (totalBlobs != null && nmethods != null && adapters != null) {
            if (fullCount != null) {
                lines.add(String.format(" total_blobs=%d, nmethods=%d, adapters=%d, full_count=%d",
                        totalBlobs, nmethods, adapters, fullCount));
            } else {
                lines.add(String.format(" total_blobs=%d nmethods=%d adapters=%d",
                        totalBlobs, nmethods, adapters));
            }
        }
        
        if (compilationEnabled != null) {
            String state = compilationEnabled ? "enabled" : "disabled";
            if (stoppedCount != null && restartedCount != null) {
                lines.add(String.format("Compilation: %s, stopped_count=%d, restarted_count=%d",
                        state, stoppedCount, restartedCount));
            } else {
                lines.add(String.format(" compilation: %s", state));
            }
        }
        
        if (trailingStoppedCount != null && trailingRestartedCount != null) {
            lines.add(String.format("              stopped_count=%d, restarted_count=%d",
                    trailingStoppedCount, trailingRestartedCount));
        }
        
        if (trailingFullCount != null) {
            lines.add(String.format(" full_count=%d", trailingFullCount));
        }
        
        // Append unparsed lines at the end (compilation events, etc.)
        lines.addAll(unparsedLines);
        return lines;
    }

    private static Parsed parse(@NotNull String header, @NotNull List<String> lines) {
        List<String> allLines = new ArrayList<>(lines.size() + 1);
        allLines.add(header);
        allLines.addAll(lines);
        List<String> originalLines = new ArrayList<>(allLines);  // Store original lines for fallback

        List<CodeHeapEntry> entries = new ArrayList<>();
        List<String> unparsedLines = new ArrayList<>();
        long totalSizeKb = 0;
        long totalUsedKb = 0;
        long totalMaxUsedKb = 0;
        long totalFreeKb = 0;
        Integer totalBlobs = null;
        Integer nmethods = null;
        Integer adapters = null;
        Integer fullCount = null;
        Boolean compilationEnabled = null;
        Integer stoppedCount = null;
        Integer restartedCount = null;
        Integer trailingStoppedCount = null;
        Integer trailingRestartedCount = null;
        Integer trailingFullCount = null;
        
        int parsedCount = 0;  // Track how many lines we recognize

        int i = 0;
        while (i < allLines.size()) {
            String line = allLines.get(i);
            
            // Empty lines are significant for reconstruction - preserve them
            if (line.trim().isEmpty()) {
                unparsedLines.add(line);
                i++;
                continue;
            }
            
            // Try CodeHeap pattern
            Matcher heap = CODE_HEAP_LINE.matcher(line);
            if (heap.matches()) {
                parsedCount++;
                String name = heap.group(1);
                long sizeKb = parseLong(heap.group(2));
                long usedKb = parseLong(heap.group(3));
                long maxUsedKb = parseLong(heap.group(4));
                long freeKb = parseLong(heap.group(5));

                String low = "0x0";
                String current = "0x0";
                String high = "0x0";
                if (i + 1 < allLines.size()) {
                    Matcher bounds = BOUNDS_LINE.matcher(allLines.get(i + 1));
                    if (bounds.matches()) {
                        parsedCount++;
                        low = bounds.group(1);
                        current = bounds.group(2);
                        high = bounds.group(3);
                        i++;
                    }
                }

                entries.add(new CodeHeapEntry(name, sizeKb, usedKb, maxUsedKb, freeKb,
                    low, current, high, null, null, null, null));
            } else {
                // Try CodeCache total line
                Matcher total = TOTAL_LINE.matcher(line);
                if (total.matches()) {
                    parsedCount++;
                    totalSizeKb = parseLong(total.group(1));
                    totalUsedKb = parseLong(total.group(2));
                    totalMaxUsedKb = parseLong(total.group(3));
                    totalFreeKb = parseLong(total.group(4));
                } else {
                    // Try total_blobs line
                    Matcher totalBlobsLine = TOTAL_BLOBS_LINE.matcher(line);
                    if (totalBlobsLine.matches()) {
                        parsedCount++;
                        totalBlobs = parseInt(totalBlobsLine.group(1));
                        if (totalBlobsLine.group(2) != null) {
                            nmethods = parseInt(totalBlobsLine.group(2));
                            adapters = parseInt(totalBlobsLine.group(3));
                            fullCount = parseInt(totalBlobsLine.group(4));
                        } else {
                            nmethods = parseInt(totalBlobsLine.group(5));
                            adapters = parseInt(totalBlobsLine.group(6));
                        }
                    } else {
                        // Try Compilation: (extended format)
                        Matcher compilationExtLine = COMPILATION_EXT_LINE.matcher(line);
                        if (compilationExtLine.matches()) {
                            parsedCount++;
                            compilationEnabled = "enabled".equals(compilationExtLine.group(1));
                            stoppedCount = parseInt(compilationExtLine.group(2));
                            restartedCount = parseInt(compilationExtLine.group(3));
                        } else {
                            // Try compilation: (simple format)
                            Matcher compilationLine = COMPILATION_LINE.matcher(line);
                            if (compilationLine.matches()) {
                                parsedCount++;
                                compilationEnabled = "enabled".equals(compilationLine.group(1));
                            } else {
                                // Try stopped/restarted line
                                Matcher stoppedRestartedLine = STOPPED_RESTARTED_LINE.matcher(line);
                                if (stoppedRestartedLine.matches()) {
                                    parsedCount++;
                                    trailingStoppedCount = parseInt(stoppedRestartedLine.group(1));
                                    trailingRestartedCount = parseInt(stoppedRestartedLine.group(2));
                                } else {
                                    // Try full_count line
                                    Matcher fullCountLine = FULL_COUNT_LINE.matcher(line);
                                    if (fullCountLine.matches()) {
                                        parsedCount++;
                                        trailingFullCount = parseInt(fullCountLine.group(1));
                                    } else {
                                        // Unrecognized line - preserve it
                                        // This includes Compilation events headers and other content
                                        unparsedLines.add(line);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            i++;
        }

        // Only use fallback if we couldn't parse almost anything significant
        // We consider it a full parse if we got:
        // - CodeHeap entries, OR
        // - CodeCache total line + blobs info, OR  
        // - Compilation info
        boolean parseFailedCompletely = parsedCount == 0 && entries.isEmpty() 
                    && totalBlobs == null && compilationEnabled == null;
        
        return new Parsed(header, entries, totalSizeKb, totalUsedKb, totalMaxUsedKb, totalFreeKb,
                totalBlobs, nmethods, adapters, fullCount, compilationEnabled, stoppedCount, restartedCount,
                trailingStoppedCount, trailingRestartedCount, trailingFullCount, unparsedLines, 
                originalLines, parseFailedCompletely);
    }

    private static long parseLong(@NotNull String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static @Nullable Integer parseInt(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Parsed(
            String header,
            List<CodeHeapEntry> entries,
            long totalSizeKb,
            long totalUsedKb,
            long totalMaxUsedKb,
            long totalFreeKb,
            Integer totalBlobs,
            Integer nmethods,
            Integer adapters,
            Integer fullCount,
            Boolean compilationEnabled,
            Integer stoppedCount,
                Integer restartedCount,
                Integer trailingStoppedCount,
                Integer trailingRestartedCount,
                Integer trailingFullCount,
                List<String> unparsedLines,
                List<String> originalLines,
                boolean parseFailedCompletely
    ) {}

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (String line : lines()) sb.append(line).append('\n');
        return sb.toString();
    }
}
