package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.*;

/**
 * Heap summary from the PROCESS section.
 *
 * <p>Header: {@code Heap:}
 * <p>Content varies by GC collector type (G1, Shenandoah, etc.).
 * Eagerly parsed accessors extract the GC type and basic heap stats.
 */
public final class HeapSummary extends NamedSection {

    public enum GcType {
        G1, SHENANDOAH, PARALLEL, SERIAL, ZGC
    }

    private final @Nullable GcType gcType;
    private final @Nullable Long totalKb;
    private final @Nullable Long usedKb;
    private final @Nullable Long committedKb;

    // G1: " garbage-first heap   total 266240K, used 58749K [0x..."
    private static final Pattern G1_PAT = Pattern.compile(
            "^\\s*garbage-first heap\\s+total\\s+(?:reserved\\s+)?(\\d+)K,\\s+committed\\s+(\\d+)K,\\s+used\\s+(\\d+)K|" +
            "^\\s*garbage-first heap\\s+total\\s+(\\d+)K,\\s+used\\s+(\\d+)K");
    // Shenandoah: "   384M max, 384M soft max, 383M committed, 305M used"
    private static final Pattern SHEN_PAT = Pattern.compile(
            "^\\s*(\\d+)([MKG])\\s+max.*?(\\d+)([MKG])\\s+committed.*?(\\d+)([MKG])\\s+used");

    @JsonCreator
    public HeapSummary(@JsonProperty("name") @NotNull String name,
                       @JsonProperty("lines") @Nullable List<String> lines) {
        super(name, lines != null ? lines : List.of());
        GcType gcType = null;
        Long total = null, used = null, committed = null;
        for (String line : lines()) {
            if (gcType == null) {
                String stripped = line.strip();
                if (stripped.startsWith("garbage-first heap")) gcType = GcType.G1;
                else if (stripped.startsWith("Shenandoah")) gcType = GcType.SHENANDOAH;
                else if (stripped.startsWith("PSYoungGen") || stripped.startsWith("par new generation")) gcType = GcType.PARALLEL;
                else if (stripped.startsWith("def new generation")) gcType = GcType.SERIAL;
                else if (stripped.startsWith("ZHeap")) gcType = GcType.ZGC;
            }
            Matcher m;
            if (total == null && (m = G1_PAT.matcher(line)).find()) {
                if (m.group(1) != null) {
                    // "total reserved NK, committed NK, used NK"
                    total = Long.parseLong(m.group(1));
                    committed = Long.parseLong(m.group(2));
                    used = Long.parseLong(m.group(3));
                } else {
                    // "total NK, used NK"
                    total = Long.parseLong(m.group(4));
                    used = Long.parseLong(m.group(5));
                }
            } else if (total == null && (m = SHEN_PAT.matcher(line)).find()) {
                total = toKb(Long.parseLong(m.group(1)), m.group(2));
                committed = toKb(Long.parseLong(m.group(3)), m.group(4));
                used = toKb(Long.parseLong(m.group(5)), m.group(6));
            }
        }
        this.gcType = gcType;
        this.totalKb = total;
        this.usedKb = used;
        this.committedKb = committed;
    }

    private static long toKb(long value, String unit) {
        return switch (unit) {
            case "G" -> value * 1024 * 1024;
            case "M" -> value * 1024;
            default -> value;
        };
    }

    public static @NotNull HeapSummary fromLines(@NotNull String name, @NotNull List<String> lines) {
        return new HeapSummary(name, lines);
    }

    /** GC collector type, or null if unrecognized. */
    @JsonIgnore public @Nullable GcType gcType() { return gcType; }
    /** Total heap size in KB (reserved for G1, max for Shenandoah). */
    @JsonIgnore public @Nullable Long totalKb() { return totalKb; }
    /** Used heap in KB. */
    @JsonIgnore public @Nullable Long usedKb() { return usedKb; }
    /** Committed heap in KB (null for older G1 format without committed). */
    @JsonIgnore public @Nullable Long committedKb() { return committedKb; }

    @Override
    public void accept(HsErrVisitor v) { v.visitHeapSummary(this); }
}
