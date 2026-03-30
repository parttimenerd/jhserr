package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The PROCESS section of the hs_err file.
 *
 * <p>Stores all sub-items in order as an {@link #items()} list, preserving
 * exact interleaving and blank lines. Each item is a typed model object
 * ({@link ThreadList}, {@link EventLog}, {@link DynamicLibraries},
 * {@link VmArguments}, {@link EnvironmentVariables}, {@link NamedSection})
 * or a {@link BlankLine} separator.
 *
 * <p>Convenience accessors search the items list for specific typed items.
 */
public class ProcessSection {

    /** Section banner, e.g. "---------------  P R O C E S S  ---------------" */
    private final @NotNull String banner;

    /** Ordered items: typed model objects or {@link BlankLine} separators */
    private final @NotNull List<ProcessSectionItem> items;

    @JsonCreator
    public ProcessSection(@JsonProperty("banner") @NotNull String banner,
                          @JsonProperty("items") @NotNull List<ProcessSectionItem> items) {
        this.banner = banner;
        this.items = List.copyOf(items);
    }

    @JsonProperty public @NotNull String banner() { return banner; }
    @JsonProperty public @NotNull List<ProcessSectionItem> items() { return items; }

    // ── generic finders ──

    /** Find the first item of the given type, or null. */
    @JsonIgnore
    public <T extends ProcessSectionItem> @Nullable T find(@NotNull Class<T> type) {
        for (ProcessSectionItem i : items) if (type.isInstance(i)) return type.cast(i);
        return null;
    }

    /** Find all items of the given type. */
    @JsonIgnore
    public <T extends ProcessSectionItem> @NotNull List<T> findAll(@NotNull Class<T> type) {
        var result = new ArrayList<T>();
        for (ProcessSectionItem i : items) if (type.isInstance(i)) result.add(type.cast(i));
        return result;
    }

    // ── convenience accessors ──

    @JsonIgnore public @Nullable UidInfo uidInfo() { return find(UidInfo.class); }
    @JsonIgnore public @Nullable UmaskInfo umaskInfo() { return find(UmaskInfo.class); }
    @JsonIgnore public @Nullable VmStateInfo vmState() { return find(VmStateInfo.class); }
    @JsonIgnore public @Nullable HeapAddressInfo heapAddress() { return find(HeapAddressInfo.class); }
    @JsonIgnore public @Nullable CompilationInfo compilationInfo() { return find(CompilationInfo.class); }
    @JsonIgnore public @Nullable GlobalFlags globalFlags() { return find(GlobalFlags.class); }

    @JsonIgnore public @Nullable ThreadList javaThreads() {
        for (ProcessSectionItem i : items) if (i instanceof ThreadList t && t.isJavaThreads()) return t;
        return null;
    }

    @JsonIgnore public @Nullable ThreadList otherThreads() {
        for (ProcessSectionItem i : items) if (i instanceof ThreadList t && !t.isJavaThreads()) return t;
        return null;
    }

    @JsonIgnore public @NotNull List<EventLog> eventLogs() { return findAll(EventLog.class); }
    @JsonIgnore public @Nullable DynamicLibraries dynamicLibraries() { return find(DynamicLibraries.class); }
    @JsonIgnore public @Nullable VmArguments vmArguments() { return find(VmArguments.class); }
    @JsonIgnore public @Nullable EnvironmentVariables environmentVariables() { return find(EnvironmentVariables.class); }
    @JsonIgnore public @Nullable SmrInfo smrInfo() { return find(SmrInfo.class); }
    @JsonIgnore public @Nullable GcPreciousLog gcPreciousLog() { return find(GcPreciousLog.class); }
    @JsonIgnore public @Nullable HeapSummary heap() { return find(HeapSummary.class); }
    @JsonIgnore public @Nullable HeapRegions heapRegions() { return find(HeapRegions.class); }
    @JsonIgnore public @Nullable MetaspaceInfo metaspace() { return find(MetaspaceInfo.class); }
    @JsonIgnore public @Nullable CodeCacheInfo codeCache() { return find(CodeCacheInfo.class); }
    @JsonIgnore public @Nullable CompileTasksInfo compileTasks() { return find(CompileTasksInfo.class); }
    @JsonIgnore public @Nullable NativeMemoryTracking nativeMemoryTracking() { return find(NativeMemoryTracking.class); }
    @JsonIgnore public @Nullable CdsInfo cdsInfo() { return find(CdsInfo.class); }
    @JsonIgnore public @Nullable PollingPageInfo pollingPage() { return find(PollingPageInfo.class); }
    @JsonIgnore public @NotNull List<NarrowKlassInfo> narrowKlassInfos() { return findAll(NarrowKlassInfo.class); }
    @JsonIgnore public @Nullable CompressedClassSpaceInfo compressedClassSpace() { return find(CompressedClassSpaceInfo.class); }
    @JsonIgnore public @Nullable SignalHandlers signalHandlers() { return find(SignalHandlers.class); }

    /** Returns only pure NamedSection items (not typed subclasses like GcPreciousLog). */
    @JsonIgnore public @NotNull List<NamedSection> namedSections() {
        var result = new ArrayList<NamedSection>();
        for (SectionItem i : items) if (i.getClass() == NamedSection.class) result.add((NamedSection) i);
        return result;
    }

    public void accept(HsErrVisitor v) {
        v.visitProcessSection(this);
        for (SectionItem item : items) item.accept(v);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(banner).append('\n');
        for (SectionItem item : items) {
            if (item instanceof BlankLine) {
                sb.append('\n');
            } else {
                sb.append(item);
            }
        }
        return sb.toString();
    }
}
