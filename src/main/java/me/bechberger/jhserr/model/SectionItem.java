package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Common type for all items stored in a section's ordered item list.
 *
 * <p>Each section ({@link ThreadSection}, {@link ProcessSection},
 * {@link SystemSection}) stores its contents as a {@code List<SectionItem>}.
 * Items are typed model objects: structured records for well-known blocks,
 * {@link NamedSection} for free-form named sub-sections, and {@link BlankLine}
 * for separator blank lines.
 *
 * <p>Sealed to the known set of model types, enabling exhaustive pattern matching.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BlankLine.class, name = "blankLine"),
        @JsonSubTypes.Type(value = ThreadInfo.class, name = "threadInfo"),
        @JsonSubTypes.Type(value = FrameList.class, name = "frameList"),
        @JsonSubTypes.Type(value = ErrorDuringError.class, name = "errorDuringError"),
        @JsonSubTypes.Type(value = Registers.class, name = "registers"),
        @JsonSubTypes.Type(value = NamedSection.class, name = "namedSection"),
        @JsonSubTypes.Type(value = OsInfo.class, name = "osInfo"),
        @JsonSubTypes.Type(value = CpuInfo.class, name = "cpuInfo"),
        @JsonSubTypes.Type(value = SmrInfo.class, name = "SmrInfo"),
        @JsonSubTypes.Type(value = CompileTasksInfo.class, name = "CompileTasksInfo"),
        @JsonSubTypes.Type(value = GcPreciousLog.class, name = "GcPreciousLog"),
        @JsonSubTypes.Type(value = HeapSummary.class, name = "HeapSummary"),
        @JsonSubTypes.Type(value = HeapRegions.class, name = "HeapRegions"),
        @JsonSubTypes.Type(value = MetaspaceInfo.class, name = "MetaspaceInfo"),
        @JsonSubTypes.Type(value = CodeCacheInfo.class, name = "CodeCacheInfo"),
        @JsonSubTypes.Type(value = LoggingConfig.class, name = "LoggingConfig"),
        @JsonSubTypes.Type(value = ActiveLocale.class, name = "ActiveLocale"),
        @JsonSubTypes.Type(value = SignalHandlers.class, name = "SignalHandlers"),
        @JsonSubTypes.Type(value = NativeMemoryTracking.class, name = "NativeMemoryTracking"),
        @JsonSubTypes.Type(value = ReleaseFileInfo.class, name = "ReleaseFileInfo"),
        @JsonSubTypes.Type(value = VmMutexInfo.class, name = "VmMutexInfo"),
        @JsonSubTypes.Type(value = ThreadList.class, name = "threadList"),
        @JsonSubTypes.Type(value = EventLog.class, name = "eventLog"),
        @JsonSubTypes.Type(value = DynamicLibraries.class, name = "dynamicLibraries"),
        @JsonSubTypes.Type(value = VmArguments.class, name = "vmArguments"),
        @JsonSubTypes.Type(value = EnvironmentVariables.class, name = "environmentVariables"),
        @JsonSubTypes.Type(value = UidInfo.class, name = "uidInfo"),
        @JsonSubTypes.Type(value = UmaskInfo.class, name = "umaskInfo"),
        @JsonSubTypes.Type(value = VmStateInfo.class, name = "vmStateInfo"),
        @JsonSubTypes.Type(value = HeapAddressInfo.class, name = "heapAddressInfo"),
        @JsonSubTypes.Type(value = CompilationInfo.class, name = "compilationInfo"),
        @JsonSubTypes.Type(value = GlobalFlags.class, name = "globalFlags"),
        @JsonSubTypes.Type(value = CdsInfo.class, name = "cdsInfo"),
        @JsonSubTypes.Type(value = CompressedClassSpaceInfo.class, name = "compressedClassSpaceInfo"),
        @JsonSubTypes.Type(value = NarrowKlassInfo.class, name = "narrowKlassInfo"),
        @JsonSubTypes.Type(value = PollingPageInfo.class, name = "pollingPageInfo"),
        @JsonSubTypes.Type(value = SignalInfo.class, name = "signalInfo"),
        @JsonSubTypes.Type(value = StackBoundsInfo.class, name = "stackBoundsInfo"),
        @JsonSubTypes.Type(value = MemoryInfo.class, name = "memoryInfo"),
        @JsonSubTypes.Type(value = VmInfo.class, name = "vmInfo"),
        @JsonSubTypes.Type(value = RegisterMemoryMapping.class, name = "RegisterMemoryMapping"),
        @JsonSubTypes.Type(value = TopOfStack.class, name = "TopOfStack")
})
public sealed interface SectionItem permits
        ThreadSectionItem,
        ProcessSectionItem,
        SystemSectionItem {

    /** Accept a visitor. Every SectionItem implementation must call the appropriate visit method. */
    void accept(HsErrVisitor v);
}
