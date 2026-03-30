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
 * GC Precious Log from the PROCESS section.
 *
 * <p>Header: {@code GC Precious Log:}
 * <p>Body lines are " key: value" pairs. Eagerly parsed accessors extract well-known fields.
 */
public final class GcPreciousLog extends NamedSection {

    private final @Nullable Integer totalCpus;
    private final @Nullable Integer availableCpus;
    private final @Nullable String memory;
    private final @Nullable String heapRegionSize;
    private final @Nullable String heapMinCapacity;
    private final @Nullable String heapInitialCapacity;
    private final @Nullable String heapMaxCapacity;
    private final @Nullable Integer parallelWorkers;
    private final @Nullable Integer concurrentWorkers;
    private final @Nullable Integer concurrentRefinementWorkers;
    private final @Nullable String compressedOops;
    private final @Nullable Boolean numaSupport;
    private final @Nullable Boolean largePageSupport;
    private final @Nullable Boolean periodicGc;

    private static final Pattern CPUS = Pattern.compile("^\\s*CPUs:\\s+(\\d+)\\s+total,\\s+(\\d+)\\s+available");
    private static final Pattern MEMORY = Pattern.compile("^\\s*Memory:\\s+(.+)$");
    private static final Pattern HEAP_REGION_SIZE = Pattern.compile("^\\s*Heap Region Size:\\s+(.+)$");
    private static final Pattern HEAP_MIN = Pattern.compile("^\\s*Heap Min Capacity:\\s+(.+)$");
    private static final Pattern HEAP_INIT = Pattern.compile("^\\s*Heap Initial Capacity:\\s+(.+)$");
    private static final Pattern HEAP_MAX = Pattern.compile("^\\s*Heap Max Capacity:\\s+(.+)$");
    private static final Pattern PARALLEL = Pattern.compile("^\\s*Parallel Workers:\\s+(\\d+)$");
    private static final Pattern CONCURRENT = Pattern.compile("^\\s*Concurrent Workers:\\s+(\\d+)$");
    private static final Pattern CONCURRENT_REF = Pattern.compile("^\\s*Concurrent Refinement Workers:\\s+(\\d+)$");
    private static final Pattern COMPRESSED = Pattern.compile("^\\s*Compressed Oops:\\s+(.+)$");
    private static final Pattern NUMA = Pattern.compile("^\\s*NUMA Support:\\s+(\\S+)");
    private static final Pattern LARGE_PAGE = Pattern.compile("^\\s*Large Page Support:\\s+(\\S+)");
    private static final Pattern PERIODIC = Pattern.compile("^\\s*Periodic GC:\\s+(\\S+)");

    @JsonCreator
    public GcPreciousLog(@JsonProperty("name") @NotNull String name,
                         @JsonProperty("lines") @Nullable List<String> lines) {
        super(name, lines != null ? lines : List.of());
        Integer totalCpus = null, availCpus = null, par = null, conc = null, concRef = null;
        String mem = null, regionSize = null, heapMin = null, heapInit = null, heapMax = null, oops = null;
        Boolean numa = null, largePage = null, periodic = null;
        for (String line : lines()) {
            Matcher m;
            if (totalCpus == null && (m = CPUS.matcher(line)).find()) { totalCpus = Integer.parseInt(m.group(1)); availCpus = Integer.parseInt(m.group(2)); }
            else if (mem == null && (m = MEMORY.matcher(line)).find()) mem = m.group(1).strip();
            else if (regionSize == null && (m = HEAP_REGION_SIZE.matcher(line)).find()) regionSize = m.group(1).strip();
            else if (heapMin == null && (m = HEAP_MIN.matcher(line)).find()) heapMin = m.group(1).strip();
            else if (heapInit == null && (m = HEAP_INIT.matcher(line)).find()) heapInit = m.group(1).strip();
            else if (heapMax == null && (m = HEAP_MAX.matcher(line)).find()) heapMax = m.group(1).strip();
            else if (par == null && (m = PARALLEL.matcher(line)).find()) par = Integer.parseInt(m.group(1));
            else if (conc == null && (m = CONCURRENT.matcher(line)).find()) conc = Integer.parseInt(m.group(1));
            else if (concRef == null && (m = CONCURRENT_REF.matcher(line)).find()) concRef = Integer.parseInt(m.group(1));
            else if (oops == null && (m = COMPRESSED.matcher(line)).find()) oops = m.group(1).strip();
            else if (numa == null && (m = NUMA.matcher(line)).find()) numa = !"Disabled".equals(m.group(1));
            else if (largePage == null && (m = LARGE_PAGE.matcher(line)).find()) largePage = !"Disabled".equals(m.group(1));
            else if (periodic == null && (m = PERIODIC.matcher(line)).find()) periodic = !"Disabled".equals(m.group(1));
        }
        this.totalCpus = totalCpus;
        this.availableCpus = availCpus;
        this.memory = mem;
        this.heapRegionSize = regionSize;
        this.heapMinCapacity = heapMin;
        this.heapInitialCapacity = heapInit;
        this.heapMaxCapacity = heapMax;
        this.parallelWorkers = par;
        this.concurrentWorkers = conc;
        this.concurrentRefinementWorkers = concRef;
        this.compressedOops = oops;
        this.numaSupport = numa;
        this.largePageSupport = largePage;
        this.periodicGc = periodic;
    }

    public static @NotNull GcPreciousLog fromLines(@NotNull String name, @NotNull List<String> lines) {
        return new GcPreciousLog(name, lines);
    }

    @JsonIgnore public @Nullable Integer totalCpus() { return totalCpus; }
    @JsonIgnore public @Nullable Integer availableCpus() { return availableCpus; }
    @JsonIgnore public @Nullable String memory() { return memory; }
    @JsonIgnore public @Nullable String heapRegionSize() { return heapRegionSize; }
    @JsonIgnore public @Nullable String heapMinCapacity() { return heapMinCapacity; }
    @JsonIgnore public @Nullable String heapInitialCapacity() { return heapInitialCapacity; }
    @JsonIgnore public @Nullable String heapMaxCapacity() { return heapMaxCapacity; }
    @JsonIgnore public @Nullable Integer parallelWorkers() { return parallelWorkers; }
    @JsonIgnore public @Nullable Integer concurrentWorkers() { return concurrentWorkers; }
    @JsonIgnore public @Nullable Integer concurrentRefinementWorkers() { return concurrentRefinementWorkers; }
    /** e.g. "Enabled (Zero based)" or "Enabled (32-bit)" or "Disabled" */
    @JsonIgnore public @Nullable String compressedOops() { return compressedOops; }
    @JsonIgnore public @Nullable Boolean numaSupport() { return numaSupport; }
    @JsonIgnore public @Nullable Boolean largePageSupport() { return largePageSupport; }
    @JsonIgnore public @Nullable Boolean periodicGc() { return periodicGc; }

    @Override
    public void accept(HsErrVisitor v) { v.visitGcPreciousLog(this); }
}
