package me.bechberger.jhserr;

import me.bechberger.jhserr.model.*;

/**
 * Visitor for walking the parsed hs_err model tree.
 * Default methods are no-ops so implementations can override only what they need.
 */
public interface HsErrVisitor {
    default void visitReport(HsErrReport report) {}
    default void visitHeader(Header header) {}
    default void visitSummary(Summary summary) {}
    default void visitThreadSection(ThreadSection thread) {}
    default void visitBlankLine(BlankLine line) {}
    default void visitCurrentThread(ThreadInfo info) {}
    default void visitNativeFrames(FrameList frames) {}
    default void visitJavaFrames(FrameList frames) {}
    default void visitFrame(StackFrame frame) {}
    default void visitRegisters(Registers registers) {}
    default void visitErrorDuringError(ErrorDuringError error) {}
    default void visitProcessSection(ProcessSection process) {}
    default void visitJavaThreadsList(ThreadList list) {}
    default void visitOtherThreadsList(ThreadList list) {}
    default void visitThreadEntry(ThreadEntry entry) {}
    default void visitEventLog(EventLog log) {}
    default void visitDynamicLibraries(DynamicLibraries libs) {}
    default void visitEnvironmentVariables(EnvironmentVariables vars) {}
    default void visitVmArguments(VmArguments args) {}
    default void visitGlobalFlags(GlobalFlags flags) {}
    default void visitSystemSection(SystemSection system) {}
    default void visitNamedSection(NamedSection section) {}
    default void visitSmrInfo(SmrInfo info) {}
    default void visitCompileTasksInfo(CompileTasksInfo info) {}
    default void visitGcPreciousLog(GcPreciousLog info) {}
    default void visitHeapSummary(HeapSummary info) {}
    default void visitHeapRegions(HeapRegions info) {}
    default void visitOsInfo(OsInfo info) {}
    default void visitCpuInfo(CpuInfo info) {}
    default void visitMetaspaceInfo(MetaspaceInfo info) {}
    default void visitCodeCacheInfo(CodeCacheInfo info) {}
    default void visitLoggingConfig(LoggingConfig info) {}
    default void visitActiveLocale(ActiveLocale info) {}
    default void visitSignalHandlers(SignalHandlers info) {}
    default void visitNativeMemoryTracking(NativeMemoryTracking info) {}
    default void visitReleaseFileInfo(ReleaseFileInfo info) {}
    default void visitVmMutexInfo(VmMutexInfo info) {}
    default void visitPollingPageInfo(PollingPageInfo info) {}
    default void visitNarrowKlassInfo(NarrowKlassInfo info) {}
    default void visitCdsInfo(CdsInfo info) {}
    default void visitCompressedClassSpaceInfo(CompressedClassSpaceInfo info) {}
    default void visitVmInfo(VmInfo info) {}
    default void visitUidInfo(UidInfo info) {}
    default void visitUmaskInfo(UmaskInfo info) {}
    default void visitVmStateInfo(VmStateInfo info) {}
    default void visitHeapAddressInfo(HeapAddressInfo info) {}
    default void visitCompilationInfo(CompilationInfo info) {}
    default void visitSignalInfo(SignalInfo info) {}
    default void visitStackBoundsInfo(StackBoundsInfo info) {}
    default void visitMemoryInfo(MemoryInfo info) {}
    default void visitRegisterMemoryMapping(RegisterMemoryMapping mapping) {}
    default void visitTopOfStack(TopOfStack stack) {}
    default void visitEnd(boolean complete) {}
}
