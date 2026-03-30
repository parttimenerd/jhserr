package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests parsing of the Linux ppc64le Internal Error sample (hserr_example).
 */
class HsErrParserLinuxTest {

    private static HsErrReport load() throws Exception {
        Path p = Path.of(HsErrParserLinuxTest.class.getResource("/hserr_example").toURI());
        return HsErrParser.parse(p);
    }

    @Test
    void headerFields() throws Exception {
        Header h = load().header();
        assertThat(h.errorType()).isEqualTo("Internal Error");
        assertThat(h.errorDetail()).contains("Internal Error");
        assertThat(h.assertDetail()).isNotNull();
        assertThat(h.jreVersion()).contains("27");
        assertThat(h.javaVm()).contains("OpenJDK");
        assertThat(h.problematicFrame()).startsWith("V");
    }

    @Test
    void summaryFields() throws Exception {
        Summary s = load().summary();
        assertThat(s).isNotNull();
        assertThat(s.hostname()).isNotNull();
        assertThat(s.time()).isNotNull();
    }

    @Test
    void threadSection() throws Exception {
        ThreadSection t = load().thread();
        assertThat(t).isNotNull();
        assertThat(t.currentThread()).isNotNull();
        assertThat(t.nativeFrames()).isNotNull();
        assertThat(t.nativeFrames().frames()).hasSizeGreaterThan(2);
        assertThat(t.nativeFrames().frames().get(0).marker()).isEqualTo('V');
        assertThat(t.registers()).isNotNull();
    }

    @Test
    void processSection() throws Exception {
        ProcessSection p = load().process();
        assertThat(p).isNotNull();

        UidInfo uid = p.uidInfo();
        assertThat(uid).isNotNull();
        assertThat(uid.uid()).isEqualTo(3670);
        assertThat(uid.euid()).isEqualTo(3670);
        assertThat(uid.gid()).isEqualTo(15);
        assertThat(uid.egid()).isEqualTo(15);

        UmaskInfo umask = p.umaskInfo();
        assertThat(umask).isNotNull();
        assertThat(umask.umask()).isEqualTo("0022");

        VmStateInfo vmState = p.vmState();
        assertThat(vmState).isNotNull();
        assertThat(vmState.state()).isEqualTo("synchronizing");
        assertThat(vmState.phase()).isEqualTo("normal execution");

        HeapAddressInfo heap = p.heapAddress();
        assertThat(heap).isNotNull();
        assertThat(heap.address()).isEqualTo("0x00000000e8000000");
        assertThat(heap.sizeMB()).isEqualTo(384);
        assertThat(heap.compressedOopsMode()).isEqualTo("32-bit");
        assertThat(heap.oopShiftAmount()).isNull();

        CompilationInfo comp = p.compilationInfo();
        assertThat(comp).isNotNull();
        assertThat(comp.enabled()).isTrue();
        assertThat(comp.stoppedCount()).isEqualTo(0);
        assertThat(comp.restartedCount()).isEqualTo(0);

        assertThat(p.javaThreads()).isNotNull();
        assertThat(p.otherThreads()).isNotNull();
        assertThat(p.dynamicLibraries()).isNotNull();
        assertThat(p.vmArguments()).isNotNull();
        assertThat(p.vmArguments().entries()).isNotEmpty();
        assertThat(p.vmArguments().valuesByKey()).containsKey("java_command");

        assertThat(p.globalFlags()).isNotNull();
        assertThat(p.globalFlags().entries()).isNotEmpty();

        assertThat(p.eventLogs()).hasSizeGreaterThan(3);
        EventLog firstLog = p.eventLogs().get(0);
        assertThat(firstLog.declaredCount()).isGreaterThanOrEqualTo(0);
        assertThat(firstLog.entries()).isNotEmpty();
    }

    @Test
    void threadEntryFieldsParsed() throws Exception {
        ProcessSection p = load().process();

        // Linux Java thread: new format with state, id, stack, size
        ThreadEntry first = p.javaThreads().entries().get(0);
        assertThat(first.threadType()).isEqualTo("JavaThread");
        assertThat(first.name()).isEqualTo("main");
        assertThat(first.state()).isEqualTo("_thread_blocked");
        assertThat(first.osThreadId()).isEqualTo("47562");
        assertThat(first.stackStart()).startsWith("0x");
        assertThat(first.stackEnd()).startsWith("0x");
        assertThat(first.stackSize()).isEqualTo("2048K");

        // Linux Other thread with SMR info
        ThreadEntry vmThread = p.otherThreads().entries().get(0);
        assertThat(vmThread.threadType()).isEqualTo("VMThread");
        assertThat(vmThread.name()).isEqualTo("VM Thread");
        assertThat(vmThread.osThreadId()).isEqualTo("47636");
        assertThat(vmThread.stackSize()).isEqualTo("2048K");
        assertThat(vmThread.smrInfo()).isNotNull();
        assertThat(vmThread.smrInfo()).contains("_threads_hazard_ptr");
    }

    @Test
    void systemSectionTypedAccessors() throws Exception {
        SystemSection s = load().system();
        assertThat(s).isNotNull();

        // OsInfo typed accessors
        OsInfo os = s.os();
        assertThat(os).isNotNull();
        assertThat(os.uname()).contains("Linux");
        assertThat(os.isLinux()).isTrue();
        assertThat(os.kernelName()).isEqualTo("Linux");
        assertThat(os.uptime()).contains("days");
        assertThat(os.rlimit()).contains("STACK");
        assertThat(os.loadAverage()).isNotNull();
        assertThat(os.libc()).contains("glibc");

        // CpuInfo typed accessors
        CpuInfo cpu = s.cpu();
        assertThat(cpu).isNotNull();
        assertThat(cpu.totalCpus()).isEqualTo(64);
        assertThat(cpu.initialActiveCpus()).isEqualTo(64);
        assertThat(cpu.features()).isNotNull();

        assertThat(s.vmInfo()).isNotNull();
        assertThat(s.vmInfo().line()).contains("OpenJDK");
    }

    @Test
    void reportIsComplete() throws Exception {
        assertThat(load().complete()).isTrue();
    }
}
