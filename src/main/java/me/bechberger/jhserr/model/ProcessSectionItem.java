package me.bechberger.jhserr.model;

/**
 * Marker for items that can appear in a {@link ProcessSection}.
 *
 * <p>Types extending {@link NamedSection} (e.g. {@link GcPreciousLog},
 * {@link HeapSummary}, {@link HeapRegions}, {@link NativeMemoryTracking})
 * inherit this interface through NamedSection and are not listed here.
 */
public sealed interface ProcessSectionItem extends SectionItem permits
        BlankLine,
        NamedSection,
        ThreadList,
        EventLog,
        DynamicLibraries,
        VmArguments,
        EnvironmentVariables,
        GlobalFlags,
        UidInfo,
        UmaskInfo,
        VmStateInfo,
        HeapAddressInfo,
        CompilationInfo,
        SmrInfo,
        CompileTasksInfo,
        MetaspaceInfo,
        CodeCacheInfo,
        LoggingConfig,
        ActiveLocale,
        SignalHandlers,
        ReleaseFileInfo,
        VmMutexInfo,
        CdsInfo,
        CompressedClassSpaceInfo,
        NarrowKlassInfo,
        PollingPageInfo {
}
