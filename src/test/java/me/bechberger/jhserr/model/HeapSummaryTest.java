package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeapSummaryTest {

    @Test
    void reconstructsLinesAndHeader() {
        List<String> lines = List.of(
                " Shenandoah Heap",
                "   384M max, 384M soft max, 383M committed, 305M used",
                "  1536 x 256K regions"
        );

        HeapSummary summary = HeapSummary.fromLines("Heap:", lines);

        assertThat(summary.name()).isEqualTo("Heap:");
        assertThat(summary.lines()).containsExactlyElementsOf(lines);
        assertThat(summary.toString()).isEqualTo("""
                Heap:
                 Shenandoah Heap
                   384M max, 384M soft max, 383M committed, 305M used
                  1536 x 256K regions
                """);
    }
}
