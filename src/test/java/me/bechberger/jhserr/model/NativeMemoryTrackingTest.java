package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NativeMemoryTrackingTest {

    @Test
    void reconstructsLinesAndHeader() {
        List<String> lines = List.of(
                "Total: reserved=1939868KB, committed=850652KB",
                "-                 Java Heap (reserved=393216KB, committed=393216KB)",
                "                            (mmap: reserved=393216KB, committed=393216KB)"
        );

        NativeMemoryTracking nmt = NativeMemoryTracking.fromLines("Native Memory Tracking:", lines);

        assertThat(nmt.name()).isEqualTo("Native Memory Tracking:");
        assertThat(nmt.lines()).containsExactlyElementsOf(lines);
        assertThat(nmt.toString()).isEqualTo("""
                Native Memory Tracking:
                Total: reserved=1939868KB, committed=850652KB
                -                 Java Heap (reserved=393216KB, committed=393216KB)
                                            (mmap: reserved=393216KB, committed=393216KB)
                """);
    }
}
