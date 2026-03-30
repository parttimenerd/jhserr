package me.bechberger.jhserr.transform;

import me.bechberger.jhserr.model.*;
import me.bechberger.jhserr.HsErrReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base class for tree-transforming visitors on an {@link HsErrReport}.
 *
 * <p>The default implementation is an <b>identity transform</b>: calling
 * {@link #transform(HsErrReport)} returns an equivalent report. Subclasses
 * override specific {@code transformXxx()} methods to modify individual nodes;
 * the infrastructure rebuilds parent containers automatically.
 *
 * <p>Key design points:
 * <ul>
 *   <li>Immutable model stays immutable — transformers build new trees, never mutate</li>
 *   <li>Returning {@code null} from {@code transformItem()} removes that item</li>
 *   <li>Container nodes (sections, frame lists, thread lists) are always rebuilt
 *       so that child modifications propagate upward</li>
 *   <li>Compose: {@code t2.transform(t1.transform(report))}</li>
 * </ul>
 *
 * <p>The {@link SectionItem} sealed interface enables exhaustive dispatch in
 * {@link #transformItem(SectionItem)}.
 */
public class HsErrTransformer {

    /**
     * Transform an entire report. Entry point.
     */
    public @NotNull HsErrReport transform(@NotNull HsErrReport report) {
        Header header = report.header() != null ? transformHeader(report.header()) : null;
        Summary summary = report.summary() != null ? transformSummary(report.summary()) : null;
        ThreadSection thread = report.thread() != null ? transformThreadSection(report.thread()) : null;
        ProcessSection process = report.process() != null ? transformProcessSection(report.process()) : null;
        SystemSection system = report.system() != null ? transformSystemSection(report.system()) : null;
        String endMarker = transformEndMarker(report.endMarker());
        return new HsErrReport(header, summary, thread, process, system, endMarker);
    }

    // ── top-level node transforms (identity by default) ─────────────────

    protected @Nullable Header transformHeader(@NotNull Header header) {
        return header;
    }

    protected @Nullable Summary transformSummary(@NotNull Summary summary) {
        return summary;
    }

    protected @NotNull String transformEndMarker(@NotNull String endMarker) {
        return endMarker;
    }

    // ── section transforms (rebuild item lists from transformed children) ─

    protected @Nullable ThreadSection transformThreadSection(@NotNull ThreadSection section) {
        List<ThreadSectionItem> items = transformItems(section.items());
        return new ThreadSection(section.banner(), items);
    }

    protected @Nullable ProcessSection transformProcessSection(@NotNull ProcessSection section) {
        List<ProcessSectionItem> items = transformItems(section.items());
        return new ProcessSection(section.banner(), items);
    }

    protected @Nullable SystemSection transformSystemSection(@NotNull SystemSection section) {
        List<SystemSectionItem> items = transformItems(section.items());
        return new SystemSection(section.banner(), items);
    }

    // ── item dispatch (sealed switch) ────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T extends SectionItem> List<T> transformItems(List<T> items) {
        var result = new ArrayList<T>(items.size());
        for (T item : items) {
            T transformed = (T) transformItem(item);
            if (transformed != null) result.add(transformed);
        }
        return result;
    }

    /**
     * Transform a single section item. Dispatches to the specific
     * {@code transformXxx()} method based on the sealed type.
     * Return {@code null} to remove the item from its parent's list.
     */
    protected @Nullable SectionItem transformItem(@NotNull SectionItem item) {
        if (item instanceof BlankLine b)              return transformBlankLine(b);
        if (item instanceof ThreadInfo ti)           return transformThreadInfo(ti);
        if (item instanceof FrameList fl)            return transformFrameList(fl);
        if (item instanceof ErrorDuringError e)      return transformErrorDuringError(e);
        if (item instanceof Registers r)             return transformRegisters(r);
        if (item instanceof ThreadList tl)           return transformThreadList(tl);
        if (item instanceof EventLog el)             return transformEventLog(el);
        if (item instanceof DynamicLibraries dl)     return transformDynamicLibraries(dl);
        if (item instanceof VmArguments va)          return transformVmArguments(va);
        if (item instanceof EnvironmentVariables ev) return transformEnvironmentVariables(ev);
        if (item instanceof UidInfo u)               return transformUidInfo(u);
        if (item instanceof UmaskInfo u)             return transformUmaskInfo(u);
        if (item instanceof VmStateInfo v)           return transformVmStateInfo(v);
        if (item instanceof HeapAddressInfo h)       return transformHeapAddressInfo(h);
        if (item instanceof CompilationInfo c)       return transformCompilationInfo(c);
        if (item instanceof ActiveLocale a)          return transformActiveLocale(a);
        if (item instanceof LoggingConfig l)         return transformLoggingConfig(l);
        if (item instanceof SignalHandlers s)        return transformSignalHandlers(s);
        if (item instanceof NativeMemoryTracking n)  return transformNativeMemoryTracking(n);
        if (item instanceof HeapRegions hr)           return transformHeapRegions(hr);
        if (item instanceof OsInfo oi)               return transformOsInfo(oi);
        if (item instanceof CpuInfo ci2)             return transformCpuInfo(ci2);
        if (item instanceof GcPreciousLog g)         return transformGcPreciousLog(g);
        if (item instanceof HeapSummary h)           return transformHeapSummary(h);
        if (item instanceof SmrInfo s)               return transformSmrInfo(s);
        if (item instanceof CompileTasksInfo ct)     return transformCompileTasksInfo(ct);
        if (item instanceof ReleaseFileInfo rf)      return transformReleaseFileInfo(rf);
        if (item instanceof VmMutexInfo vm)          return transformVmMutexInfo(vm);
        if (item instanceof GlobalFlags g)           return transformGlobalFlags(g);
        if (item instanceof SignalInfo s)              return transformSignalInfo(s);
        if (item instanceof StackBoundsInfo s)         return transformStackBoundsInfo(s);
        if (item instanceof MemoryInfo m)               return transformMemoryInfo(m);
        if (item instanceof VmInfo vi)                    return transformVmInfo(vi);
        if (item instanceof MetaspaceInfo mi)            return transformMetaspaceInfo(mi);
        if (item instanceof CodeCacheInfo ci)            return transformCodeCacheInfo(ci);
        if (item instanceof NamedSection ns)              return transformNamedSection(ns);
        return item;
    }

    // ── individual item transforms (identity by default) ─────────────────

    protected @Nullable SectionItem transformBlankLine(@NotNull BlankLine line) {
        return line;
    }

    protected @Nullable SectionItem transformThreadInfo(@NotNull ThreadInfo info) {
        return info;
    }

    /**
     * Transforms a frame list, including each contained stack frame.
     * Frames returning {@code null} from {@link #transformStackFrame} are removed.
     */
    protected @Nullable SectionItem transformFrameList(@NotNull FrameList frameList) {
        List<StackFrame> frames = new ArrayList<>();
        for (StackFrame f : frameList.frames()) {
            StackFrame tf = transformStackFrame(f);
            if (tf != null) frames.add(tf);
        }
        return new FrameList(frameList.headerLine(), frames);
    }

    protected @Nullable SectionItem transformErrorDuringError(@NotNull ErrorDuringError error) {
        return error;
    }

    protected @Nullable SectionItem transformRegisters(@NotNull Registers registers) {
        return registers;
    }

    protected @Nullable SectionItem transformNamedSection(@NotNull NamedSection section) {
        return section;
    }

    protected @Nullable SectionItem transformHeapRegions(@NotNull HeapRegions info) {
        return info;
    }

    protected @Nullable SectionItem transformOsInfo(@NotNull OsInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformCpuInfo(@NotNull CpuInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformUidInfo(@NotNull UidInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformUmaskInfo(@NotNull UmaskInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformVmStateInfo(@NotNull VmStateInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformHeapAddressInfo(@NotNull HeapAddressInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformCompilationInfo(@NotNull CompilationInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformActiveLocale(@NotNull ActiveLocale info) {
        return info;
    }

    protected @Nullable SectionItem transformLoggingConfig(@NotNull LoggingConfig info) {
        return info;
    }

    protected @Nullable SectionItem transformSignalHandlers(@NotNull SignalHandlers info) {
        return info;
    }

    protected @Nullable SectionItem transformNativeMemoryTracking(@NotNull NativeMemoryTracking info) {
        return info;
    }

    protected @Nullable SectionItem transformGcPreciousLog(@NotNull GcPreciousLog info) {
        return info;
    }

    protected @Nullable SectionItem transformHeapSummary(@NotNull HeapSummary info) {
        return info;
    }

    protected @Nullable SectionItem transformSmrInfo(@NotNull SmrInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformCompileTasksInfo(@NotNull CompileTasksInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformReleaseFileInfo(@NotNull ReleaseFileInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformVmMutexInfo(@NotNull VmMutexInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformGlobalFlags(@NotNull GlobalFlags flags) {
        return flags;
    }

    protected @Nullable SectionItem transformSignalInfo(@NotNull SignalInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformStackBoundsInfo(@NotNull StackBoundsInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformMemoryInfo(@NotNull MemoryInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformVmInfo(@NotNull VmInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformMetaspaceInfo(@NotNull MetaspaceInfo info) {
        return info;
    }

    protected @Nullable SectionItem transformCodeCacheInfo(@NotNull CodeCacheInfo info) {
        return info;
    }

    /**
     * Transforms a thread list, including each contained thread entry.
     * Entries returning {@code null} from {@link #transformThreadEntry} are removed.
     */
    protected @Nullable SectionItem transformThreadList(@NotNull ThreadList threadList) {
        List<ThreadEntry> entries = new ArrayList<>();
        for (ThreadEntry e : threadList.entries()) {
            ThreadEntry te = transformThreadEntry(e);
            if (te != null) entries.add(te);
        }
        return new ThreadList(threadList.header(), entries, threadList.totalLine());
    }

    protected @Nullable SectionItem transformEventLog(@NotNull EventLog eventLog) {
        return eventLog;
    }

    protected @Nullable SectionItem transformDynamicLibraries(@NotNull DynamicLibraries libs) {
        return libs;
    }

    protected @Nullable SectionItem transformVmArguments(@NotNull VmArguments args) {
        return args;
    }

    protected @Nullable SectionItem transformEnvironmentVariables(@NotNull EnvironmentVariables vars) {
        return vars;
    }

    // ── nested element transforms ────────────────────────────────────────

    /** Transform a single thread entry. Return {@code null} to remove it. */
    protected @Nullable ThreadEntry transformThreadEntry(@NotNull ThreadEntry entry) {
        return entry;
    }

    /** Transform a single stack frame. Return {@code null} to remove it. */
    protected @Nullable StackFrame transformStackFrame(@NotNull StackFrame frame) {
        return frame;
    }
}
