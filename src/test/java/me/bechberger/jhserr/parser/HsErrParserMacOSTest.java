package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests parsing of the macOS aarch64 SIGSEGV sample (hs_err.log).
 */
class HsErrParserMacOSTest {

    private static HsErrReport load() throws Exception {
        Path p = Path.of(HsErrParserMacOSTest.class.getResource("/hs_err.log").toURI());
        return HsErrParser.parse(p);
    }

    @Test
    void headerFields() throws Exception {
        Header h = load().header();
        assertThat(h.errorType()).isEqualTo("SIGSEGV");
        assertThat(h.errorDetail()).contains("SIGSEGV").contains("pid=16540");
        assertThat(h.pid()).isEqualTo("16540");
        assertThat(h.tid()).isEqualTo("8451");
        assertThat(h.problematicFrame()).isNotNull();
        assertThat(h.problematicFrame().type()).isEqualTo(me.bechberger.jhserr.model.StackFrame.Type.NATIVE);
        assertThat(h.problematicFrame().body()).startsWith("[libSmallProfiler.so");
        assertThat(h.jreVersion()).contains("22");
        assertThat(h.javaVm()).contains("OpenJDK");
    }

    @Test
    void summaryFields() throws Exception {
        Summary s = load().summary();
        assertThat(s).isNotNull();
        assertThat(s.commandLine()).contains("SmallProfiler");
        assertThat(s.hostname()).isEqualTo("TESTHOST01");
        assertThat(s.time()).contains("2023");
    }

    @Test
    void threadSection() throws Exception {
        ThreadSection t = load().thread();
        assertThat(t).isNotNull();
        assertThat(t.currentThread()).isNotNull();
        assertThat(t.currentThread().name()).isEqualTo("main");
        assertThat(t.currentThread().threadType()).isEqualTo("JavaThread");

        assertThat(t.nativeFrames()).isNotNull();
        assertThat(t.nativeFrames().frames()).hasSizeGreaterThan(5);
        assertThat(t.nativeFrames().frames().get(0).type()).isEqualTo(StackFrame.Type.NATIVE);
        assertThat(t.nativeFrames().frames().get(0).line()).startsWith("C");

        assertThat(t.javaFrames()).isNotNull();
        assertThat(t.javaFrames().frames()).hasSizeGreaterThan(2);
        assertThat(t.javaFrames().frames().get(0).marker()).isIn('J', 'j', 'v', 'V');

        assertThat(t.signalInfo()).isNotNull();
        assertThat(t.signalInfo().signalName()).isEqualTo("SIGSEGV");
        assertThat(t.registers()).isNotNull();
        assertThat(t.registers().values()).containsKey("x0");

        assertThat(t.errors()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void processSection() throws Exception {
        ProcessSection p = load().process();
        assertThat(p).isNotNull();

        UidInfo uid = p.uidInfo();
        assertThat(uid).isNotNull();
        assertThat(uid.uid()).isEqualTo(1000);
        assertThat(uid.euid()).isEqualTo(1000);
        assertThat(uid.gid()).isEqualTo(20);
        assertThat(uid.egid()).isEqualTo(20);

        UmaskInfo umask = p.umaskInfo();
        assertThat(umask).isNotNull();
        assertThat(umask.umask()).isEqualTo("0022");
        assertThat(umask.perms()).isEqualTo("----w--w-");

        assertThat(p.javaThreads()).isNotNull();
        assertThat(p.javaThreads().entries()).hasSizeGreaterThan(5);
        assertThat(p.javaThreads().entries().get(0).current()).isTrue();
        assertThat(p.javaThreads().entries().get(0).name()).isEqualTo("main");

        assertThat(p.otherThreads()).isNotNull();
        assertThat(p.otherThreads().entries()).hasSizeGreaterThan(5);

        VmStateInfo vmState = p.vmState();
        assertThat(vmState).isNotNull();
        assertThat(vmState.state()).isEqualTo("not at safepoint");
        assertThat(vmState.phase()).isEqualTo("normal execution");

        HeapAddressInfo heap = p.heapAddress();
        assertThat(heap).isNotNull();
        assertThat(heap.address()).isEqualTo("0x0000000700000000");
        assertThat(heap.sizeMB()).isEqualTo(4096);
        assertThat(heap.compressedOopsMode()).isEqualTo("Zero based");
        assertThat(heap.oopShiftAmount()).isEqualTo(3);

        assertThat(p.dynamicLibraries()).isNotNull();
        assertThat(p.dynamicLibraries().entries()).hasSizeGreaterThan(10);

        assertThat(p.vmArguments()).isNotNull();
        assertThat(p.vmArguments().launcherType()).isEqualTo("SUN_STANDARD");
        assertThat(p.vmArguments().entries()).isNotEmpty();
        assertThat(p.vmArguments().valuesByKey()).containsKey("java_command");

        assertThat(p.globalFlags()).isNotNull();
        assertThat(p.globalFlags().entries()).isNotEmpty();

        assertThat(p.environmentVariables()).isNotNull();
        assertThat(p.environmentVariables().vars()).containsKey("PATH");

        assertThat(p.eventLogs()).hasSizeGreaterThan(3);
        EventLog firstLog = p.eventLogs().get(0);
        assertThat(firstLog.name()).isNotBlank();
        assertThat(firstLog.declaredCount()).isGreaterThanOrEqualTo(0);
        assertThat(firstLog.entries()).isNotEmpty();
    }

    @Test
    void threadEntryFieldsParsed() throws Exception {
        ProcessSection p = load().process();

        // Java thread: =>0x... JavaThread "main" [_thread_in_vm, id=8451, stack(0x...,0x...) (2060K)]
        ThreadEntry main = p.javaThreads().entries().get(0);
        assertThat(main.current()).isTrue();
        assertThat(main.address()).isEqualTo("0x0000000116808210");
        assertThat(main.threadType()).isEqualTo("JavaThread");
        assertThat(main.name()).isEqualTo("main");
        assertThat(main.daemon()).isFalse();
        assertThat(main.state()).isEqualTo("_thread_in_vm");
        assertThat(main.osThreadId()).isEqualTo("8451");
        assertThat(main.stackStart()).startsWith("0x");
        assertThat(main.stackEnd()).startsWith("0x");
        assertThat(main.stackSize()).isEqualTo("2060K");
        assertThat(main.isJavaThread()).isTrue();
        assertThat(main.smrInfo()).isNull();

        // Daemon thread
        ThreadEntry refHandler = p.javaThreads().entries().get(1);
        assertThat(refHandler.name()).isEqualTo("Reference Handler");
        assertThat(refHandler.daemon()).isTrue();
        assertThat(refHandler.state()).isEqualTo("_thread_blocked");
        assertThat(refHandler.current()).isFalse();

        // Other thread (non-Java): no state, no daemon
        ThreadEntry vmThread = p.otherThreads().entries().get(0);
        assertThat(vmThread.threadType()).isEqualTo("VMThread");
        assertThat(vmThread.name()).isEqualTo("VM Thread");
        assertThat(vmThread.state()).isNull();
        assertThat(vmThread.daemon()).isFalse();
        assertThat(vmThread.osThreadId()).isNotNull();
        assertThat(vmThread.stackSize()).isEqualTo("2060K");
        assertThat(vmThread.isJavaThread()).isFalse();
    }

    @Test
    void systemSection() throws Exception {
        SystemSection s = load().system();
        assertThat(s).isNotNull();
        assertThat(s.os()).isNotNull();
        assertThat(s.os().name()).isEqualTo("OS:");
        assertThat(s.os().uname()).startsWith("Darwin");
        assertThat(s.os().uptime()).contains("days");
        assertThat(s.os().rlimit()).contains("STACK");
        assertThat(s.os().loadAverage()).isNotNull();
        assertThat(s.os().isLinux()).isFalse();
        assertThat(s.os().kernelName()).isEqualTo("Darwin");
        assertThat(s.cpu()).isNotNull();
        assertThat(s.cpu().totalCpus()).isEqualTo(8);
        assertThat(s.cpu().initialActiveCpus()).isEqualTo(8);
        assertThat(s.cpu().cpuBrand()).isEqualTo("Apple M1");
        assertThat(s.memory()).isNotNull();
        assertThat(s.vmInfo()).isNotNull();
        assertThat(s.vmInfo().line()).contains("OpenJDK");
    }

    @Test
    void reportIsComplete() throws Exception {
        assertThat(load().complete()).isTrue();
    }

    /** Tests the older JDK format (no padding, no stack size, [stack: ...] [id=N] for Other Threads) */
    @Test
    void oldFormatThreadEntryFields() throws Exception {
        Path p = Path.of(getClass().getResource("/hs_err_pid42500.log").toURI());
        HsErrReport report = HsErrParser.parse(p);
        ProcessSection proc = report.process();

        // Old format JavaThread: [_thread_in_native, id=5123, stack(0x...,0x...)]  (no stack size)
        ThreadEntry main = proc.javaThreads().entries().get(0);
        assertThat(main.current()).isTrue();
        assertThat(main.name()).isEqualTo("main");
        assertThat(main.state()).isEqualTo("_thread_in_native");
        assertThat(main.osThreadId()).isEqualTo("5123");
        assertThat(main.stackStart()).startsWith("0x");
        assertThat(main.stackEnd()).startsWith("0x");
        assertThat(main.stackSize()).isNull(); // old format has no stack size

        // Old format Other thread: [stack: 0x...,0x...] [id=20483]
        ThreadEntry vmThread = proc.otherThreads().entries().get(0);
        assertThat(vmThread.threadType()).isEqualTo("VMThread");
        assertThat(vmThread.name()).isEqualTo("VM Thread");
        assertThat(vmThread.state()).isNull();
        assertThat(vmThread.osThreadId()).isEqualTo("20483");
        assertThat(vmThread.stackStart()).startsWith("0x");
        assertThat(vmThread.stackEnd()).startsWith("0x");
        assertThat(vmThread.stackSize()).isNull();
    }
}
