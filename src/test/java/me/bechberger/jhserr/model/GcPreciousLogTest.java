package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GcPreciousLogTest {

    @Test
    void reconstructsLinesAndHeader() {
        List<String> lines = List.of(
                " CardTable entry size: 512",
                " Heap Max Capacity: 384M",
                " Concurrent Workers: 16"
        );

        GcPreciousLog log = GcPreciousLog.fromLines("GC Precious Log:", lines);

        assertThat(log.name()).isEqualTo("GC Precious Log:");
        assertThat(log.lines()).containsExactlyElementsOf(lines);
        assertThat(log.toString()).isEqualTo("""
                GC Precious Log:
                 CardTable entry size: 512
                 Heap Max Capacity: 384M
                 Concurrent Workers: 16
                """);
    }
}
