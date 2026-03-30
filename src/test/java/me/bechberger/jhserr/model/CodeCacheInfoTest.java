package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeCacheInfoTest {

    @Test
    void parsesAndReconstructsCodeHeapHeaderStyle() {
        String header = "CodeHeap 'non-profiled nmethods': size=120032Kb used=1193Kb max_used=1193Kb free=118838Kb";
        List<String> body = List.of(
                " bounds [0x0000000115ecc000, 0x000000011613c000, 0x000000011d404000]",
                "CodeHeap 'profiled nmethods': size=120016Kb used=4039Kb max_used=4039Kb free=115976Kb",
                " bounds [0x000000010e404000, 0x000000010e804000, 0x0000000115938000]",
                "CodeHeap 'non-nmethods': size=5712Kb used=1270Kb max_used=1303Kb free=4441Kb",
                " bounds [0x0000000115938000, 0x0000000115ba8000, 0x0000000115ecc000]",
                " total_blobs=2787 nmethods=2292 adapters=410",
                " compilation: enabled",
                "              stopped_count=0, restarted_count=0",
                " full_count=0"
        );

        CodeCacheInfo info = new CodeCacheInfo(header, body);

        assertThat(info.entries()).hasSize(3);
        assertThat(info.totalBlobs()).isEqualTo(2787);
        assertThat(info.nmethods()).isEqualTo(2292);
        assertThat(info.adapters()).isEqualTo(410);
        assertThat(info.compilationEnabled()).isTrue();
        assertThat(info.trailingStoppedCount()).isEqualTo(0);
        assertThat(info.trailingRestartedCount()).isEqualTo(0);
        assertThat(info.trailingFullCount()).isEqualTo(0);

        assertThat(info.lines()).containsExactlyElementsOf(body);
    }

    @Test
    void parsesAndReconstructsCodeCacheHeaderStyle() {
        String header = "CodeCache: size=245824Kb, used=2006Kb, max_used=4290Kb, free=243816Kb";
        List<String> body = List.of(
                " total_blobs=694, nmethods=302, adapters=303, full_count=0",
                "Compilation: enabled, stopped_count=0, restarted_count=0"
        );

        CodeCacheInfo info = new CodeCacheInfo(header, body);

        assertThat(info.entries()).isEmpty();
        assertThat(info.totalSizeKb()).isEqualTo(245824);
        assertThat(info.totalUsedKb()).isEqualTo(2006);
        assertThat(info.totalMaxUsedKb()).isEqualTo(4290);
        assertThat(info.totalFreeKb()).isEqualTo(243816);
        assertThat(info.totalBlobs()).isEqualTo(694);
        assertThat(info.nmethods()).isEqualTo(302);
        assertThat(info.adapters()).isEqualTo(303);
        assertThat(info.fullCount()).isEqualTo(0);
        assertThat(info.compilationEnabled()).isTrue();
        assertThat(info.stoppedCount()).isEqualTo(0);
        assertThat(info.restartedCount()).isEqualTo(0);

        assertThat(info.lines()).containsExactlyElementsOf(body);
    }
}
