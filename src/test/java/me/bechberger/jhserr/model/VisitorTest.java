package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.parser.HsErrParser;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the visitor pattern: ensures accept() calls appropriate visit methods.
 */
class VisitorTest {

    private HsErrReport loadMacOS() throws Exception {
        Path p = Path.of(VisitorTest.class.getResource("/hs_err.log").toURI());
        return HsErrParser.parse(p);
    }

    @Test
    void visitorCallsAllMainMethods() throws Exception {
        HsErrReport report = loadMacOS();
        AtomicBoolean visitedReport = new AtomicBoolean();
        AtomicBoolean visitedHeader = new AtomicBoolean();
        AtomicBoolean visitedSummary = new AtomicBoolean();
        AtomicBoolean visitedThread = new AtomicBoolean();
        AtomicBoolean visitedProcess = new AtomicBoolean();
        AtomicBoolean visitedSystem = new AtomicBoolean();
        AtomicBoolean visitedEnd = new AtomicBoolean();

        report.accept(new HsErrVisitor() {
            @Override public void visitReport(HsErrReport r) { visitedReport.set(true); }
            @Override public void visitHeader(Header h) { visitedHeader.set(true); }
            @Override public void visitSummary(Summary s) { visitedSummary.set(true); }
            @Override public void visitThreadSection(ThreadSection t) { visitedThread.set(true); }
            @Override public void visitProcessSection(ProcessSection p) { visitedProcess.set(true); }
            @Override public void visitSystemSection(SystemSection s) { visitedSystem.set(true); }
            @Override public void visitEnd(boolean complete) { visitedEnd.set(true); }
        });

        assertThat(visitedReport).isTrue();
        assertThat(visitedHeader).isTrue();
        assertThat(visitedSummary).isTrue();
        assertThat(visitedThread).isTrue();
        assertThat(visitedProcess).isTrue();
        assertThat(visitedSystem).isTrue();
        assertThat(visitedEnd).isTrue();
    }

    @Test
    void visitorCountsFrames() throws Exception {
        HsErrReport report = loadMacOS();
        AtomicInteger frameCount = new AtomicInteger();

        report.accept(new HsErrVisitor() {
            @Override
            public void visitFrame(StackFrame frame) {
                frameCount.incrementAndGet();
            }
        });

        // The macOS sample has native + java frames
        assertThat(frameCount.get()).isGreaterThan(10);
    }

    @Test
    void visitorCountsThreadEntries() throws Exception {
        HsErrReport report = loadMacOS();
        AtomicInteger entryCount = new AtomicInteger();

        report.accept(new HsErrVisitor() {
            @Override
            public void visitThreadEntry(ThreadEntry entry) {
                entryCount.incrementAndGet();
            }
        });

        // 11 Java threads + 12 Other threads = 23
        assertThat(entryCount.get()).isGreaterThanOrEqualTo(20);
    }
}
