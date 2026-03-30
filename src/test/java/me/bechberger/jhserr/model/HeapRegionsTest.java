package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeapRegionsTest {

    @Test
    void reconstructsLinesAndHeader() {
        List<String> lines = List.of(
                "|    0|EC |F|BTE     e8000000,     e8000000,     e8040000|TAMS     e8000000|U     0B|",
                "|    1|R  |Y|BTE     e8040000,     e8080000,     e8080000|TAMS     e8080000|U   256K|"
        );

        HeapRegions regions = HeapRegions.fromLines("Heap Regions:", lines);

        assertThat(regions.name()).isEqualTo("Heap Regions:");
        assertThat(regions.lines()).containsExactlyElementsOf(lines);
        assertThat(regions.toString()).isEqualTo("""
                Heap Regions:
                |    0|EC |F|BTE     e8000000,     e8000000,     e8040000|TAMS     e8000000|U     0B|
                |    1|R  |Y|BTE     e8040000,     e8080000,     e8080000|TAMS     e8080000|U   256K|
                """);
    }
}
