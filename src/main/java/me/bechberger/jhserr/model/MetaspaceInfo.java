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
 * Parsed Metaspace configuration.
 * Handles multiple variants: simple summary, class+non-class, generational.
 * <p>
 * Formats:
 * - Simple: Metaspace: used 818K, committed 1024K, reserved 1114112K
 * - Class+NonClass:
 *     non-class space: used 512K, committed 768K, reserved 50000K
 *     class space: used 306K, committed 256K, reserved 1048576K
 */
public record MetaspaceInfo(
        @JsonProperty("header") @NotNull String header,
        @JsonProperty("total") @Nullable MemoryStats total,
    @JsonProperty("totalLabel") @Nullable String totalLabel,
        @JsonProperty("nonClass") @Nullable MemoryStats nonClass,
    @JsonProperty("nonClassLabel") @Nullable String nonClassLabel,
        @JsonProperty("classSpace") @Nullable MemoryStats classSpace,
    @JsonProperty("classSpaceLabel") @Nullable String classSpaceLabel,
        @JsonProperty("reclaimPolicy") @Nullable String reclaimPolicy,
    @JsonProperty("detailLines") @NotNull List<String> detailLines
) implements ProcessSectionItem {

    private static final Pattern STATS = Pattern.compile(
        "^(\\s*(?:Metaspace|non-class space|class space)\\s*)used\\s+(\\d+)([KMG]),\\s+committed\\s+(\\d+)([KMG]),\\s+reserved\\s+(\\d+)([KMG]).*$");
    private static final Pattern NON_CLASS = Pattern.compile("^\\s*non-class space.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_SPACE = Pattern.compile("^\\s*class space.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile("^\\s*Metaspace\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern POLICY = Pattern.compile("^\\s*MetaspaceReclaimPolicy:\\s*(.+)$");

    @JsonCreator
    public MetaspaceInfo(
            @JsonProperty("header") @NotNull String header,
            @JsonProperty("total") @Nullable MemoryStats total,
            @JsonProperty("totalLabel") @Nullable String totalLabel,
            @JsonProperty("nonClass") @Nullable MemoryStats nonClass,
            @JsonProperty("nonClassLabel") @Nullable String nonClassLabel,
            @JsonProperty("classSpace") @Nullable MemoryStats classSpace,
            @JsonProperty("classSpaceLabel") @Nullable String classSpaceLabel,
            @JsonProperty("reclaimPolicy") @Nullable String reclaimPolicy,
            @JsonProperty("detailLines") @Nullable List<String> detailLines) {
        this.header = header;
        this.total = total;
        this.totalLabel = totalLabel;
        this.nonClass = nonClass;
        this.nonClassLabel = nonClassLabel;
        this.classSpace = classSpace;
        this.classSpaceLabel = classSpaceLabel;
        this.reclaimPolicy = reclaimPolicy;
        this.detailLines = detailLines != null ? detailLines : new ArrayList<>();
    }

    /**
     * Parser-compatible constructor used by {@link me.bechberger.jhserr.parser.HsErrParser}.
     */
    public MetaspaceInfo(@NotNull String header, @NotNull List<String> lines) {
        this(parse(header, lines));
    }

    private MetaspaceInfo(@NotNull Parsed parsed) {
        this(parsed.header, parsed.total, parsed.totalLabel, parsed.nonClass, parsed.nonClassLabel, parsed.classSpace, parsed.classSpaceLabel,
                parsed.reclaimPolicy, parsed.detailLines);
    }

    @Override
    @JsonIgnore
    public void accept(HsErrVisitor v) {
        v.visitMetaspaceInfo(this);
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (total != null) {
            lines.add(formatStatsLine(totalLabel != null ? totalLabel : "Metaspace ", total));
        }
        if (nonClass != null) {
            lines.add(formatStatsLine(nonClassLabel != null ? nonClassLabel : " non-class space ", nonClass));
        }
        if (classSpace != null) {
            lines.add(formatStatsLine(classSpaceLabel != null ? classSpaceLabel : " class space ", classSpace));
        }
        if (reclaimPolicy != null) {
            lines.add("MetaspaceReclaimPolicy: " + reclaimPolicy);
        }
        lines.addAll(detailLines);
        return lines;
    }

    private static @NotNull String formatStatsLine(@NotNull String label, @NotNull MemoryStats stats) {
        return String.format("%sused %dK, committed %dK, reserved %dK",
                label, stats.usedKb(), stats.committedKb(), stats.reservedKb());
    }

    private static Parsed parse(@NotNull String header, @NotNull List<String> lines) {
        MemoryStats total = null;
        String totalLabel = null;
        MemoryStats nonClass = null;
        String nonClassLabel = null;
        MemoryStats classSpace = null;
        String classSpaceLabel = null;
        String reclaimPolicy = null;
        List<String> detailLines = new ArrayList<>();

        for (String line : lines) {
            Matcher policy = POLICY.matcher(line);
            if (policy.matches()) {
                reclaimPolicy = policy.group(1).trim();
                continue;
            }

            Matcher stats = STATS.matcher(line);
            if (stats.matches()) {
                String label = stats.group(1);
                MemoryStats parsed = new MemoryStats(
                        toKb(stats.group(2), stats.group(3)),
                        toKb(stats.group(4), stats.group(5)),
                        toKb(stats.group(6), stats.group(7))
                );

                if (NON_CLASS.matcher(label).matches()) {
                    nonClass = parsed;
                    nonClassLabel = label;
                } else if (CLASS_SPACE.matcher(label).matches()) {
                    classSpace = parsed;
                    classSpaceLabel = label;
                } else if (TOTAL.matcher(label).matches()) {
                    total = parsed;
                    totalLabel = label;
                } else {
                    detailLines.add(line);
                }
            } else {
                detailLines.add(line);
            }
        }

        return new Parsed(header, total, totalLabel, nonClass, nonClassLabel, classSpace, classSpaceLabel, reclaimPolicy, detailLines);
    }

    private static long toKb(@NotNull String number, @NotNull String unit) {
        long value;
        try {
            value = Long.parseLong(number);
        } catch (NumberFormatException e) {
            return 0;
        }

        return switch (unit) {
            case "G" -> value * 1024 * 1024;
            case "M" -> value * 1024;
            default -> value;
        };
    }

    private record Parsed(
            String header,
            MemoryStats total,
                String totalLabel,
            MemoryStats nonClass,
                String nonClassLabel,
            MemoryStats classSpace,
                String classSpaceLabel,
            String reclaimPolicy,
                List<String> detailLines
    ) {}

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (String line : lines()) sb.append(line).append('\n');
        return sb.toString();
    }
}