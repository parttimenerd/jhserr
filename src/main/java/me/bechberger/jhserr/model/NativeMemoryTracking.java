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
 * Native Memory Tracking (NMT) report from the PROCESS section.
 *
 * <p>Header: {@code Native Memory Tracking:}
 * <p>Eagerly parses the Total line for reserved/committed/malloc/mmap stats.
 */
public final class NativeMemoryTracking extends NamedSection {

    private final @Nullable Long totalReservedKb;
    private final @Nullable Long totalCommittedKb;
    private final @Nullable Long mallocKb;
    private final @Nullable Long mmapReservedKb;
    private final @Nullable Long mmapCommittedKb;

    // "Total: reserved=2127901KB, committed=493085KB"
    private static final Pattern TOTAL_PAT = Pattern.compile(
            "^Total:\\s+reserved=(\\d+)KB,\\s+committed=(\\d+)KB");
    // "       malloc: 54557KB #54490"  or  "       malloc: 54557KB #54490, peak=..."
    private static final Pattern MALLOC_PAT = Pattern.compile(
            "^\\s+malloc:\\s+(\\d+)KB");
    // "       mmap:   reserved=2073344KB, committed=438528KB"
    private static final Pattern MMAP_PAT = Pattern.compile(
            "^\\s+mmap:\\s+reserved=(\\d+)KB,\\s+committed=(\\d+)KB");

    @JsonCreator
    public NativeMemoryTracking(@JsonProperty("name") @NotNull String name,
                                @JsonProperty("lines") @Nullable List<String> lines) {
        super(name, lines != null ? lines : List.of());
        Long totalRes = null, totalCom = null, malloc = null, mmapRes = null, mmapCom = null;
        for (String line : lines()) {
            Matcher m;
            if (totalRes == null && (m = TOTAL_PAT.matcher(line)).find()) {
                totalRes = Long.parseLong(m.group(1));
                totalCom = Long.parseLong(m.group(2));
            } else if (malloc == null && (m = MALLOC_PAT.matcher(line)).find()) {
                malloc = Long.parseLong(m.group(1));
            } else if (mmapRes == null && (m = MMAP_PAT.matcher(line)).find()) {
                mmapRes = Long.parseLong(m.group(1));
                mmapCom = Long.parseLong(m.group(2));
            }
        }
        this.totalReservedKb = totalRes;
        this.totalCommittedKb = totalCom;
        this.mallocKb = malloc;
        this.mmapReservedKb = mmapRes;
        this.mmapCommittedKb = mmapCom;
    }

    public static @NotNull NativeMemoryTracking fromLines(@NotNull String name, @NotNull List<String> lines) {
        return new NativeMemoryTracking(name, lines);
    }

    /** Total reserved memory in KB. */
    @JsonIgnore public @Nullable Long totalReservedKb() { return totalReservedKb; }
    /** Total committed memory in KB. */
    @JsonIgnore public @Nullable Long totalCommittedKb() { return totalCommittedKb; }
    /** malloc total in KB. */
    @JsonIgnore public @Nullable Long mallocKb() { return mallocKb; }
    /** mmap reserved in KB. */
    @JsonIgnore public @Nullable Long mmapReservedKb() { return mmapReservedKb; }
    /** mmap committed in KB. */
    @JsonIgnore public @Nullable Long mmapCommittedKb() { return mmapCommittedKb; }

    @Override
    public void accept(HsErrVisitor v) { v.visitNativeMemoryTracking(this); }
}
