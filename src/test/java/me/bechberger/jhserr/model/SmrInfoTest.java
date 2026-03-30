package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmrInfoTest {

    @Test
    void parsesSectionWithMetricsAndReconstructs() {
        List<String> lines = List.of(
                "_java_thread_list=0x00007fff8c3bb230, length=11, elements={",
                "0x00007fff8c0bcaa0, 0x00007fff8c2d8e30, 0x00007fff8c2da0c0, 0x00007fff8c2dc6c0,",
                "0x00007fff8c2e2150, 0x00007fff8c2e4110, 0x00007fff8c2e6fa0, 0x00007fff8c2f1e20,",
                "0x00007fff8c4210c0, 0x00007fff8c400460, 0x00007fff8c481fc0",
                "}",
                "_java_thread_list_alloc_cnt=20, _java_thread_list_free_cnt=18, _java_thread_list_max=15, _nested_thread_list_max=1",
                "_tlh_cnt=623, _tlh_times=357, avg_tlh_time=0.57, _tlh_time_max=19",
                "_deleted_thread_cnt=4, _deleted_thread_times=0, avg_deleted_thread_time=0.00, _deleted_thread_time_max=0",
                "_delete_lock_wait_cnt=0, _delete_lock_wait_max=0",
                "_to_delete_list_cnt=0, _to_delete_list_max=2"
        );

        SmrInfo info = SmrInfo.fromLines("Threads class SMR info:", lines);

        assertThat(info.javaThreadList()).isNotNull();
        assertThat(info.javaThreadList().listAddress()).isEqualTo("0x00007fff8c3bb230");
        assertThat(info.javaThreadList().length()).isEqualTo(11);
        assertThat(info.javaThreadList().addresses()).hasSize(11);
        assertThat(info.javaThreadList().addressesPerLine()).containsExactly(4, 4, 3);
        assertThat(info.listBlocks()).hasSize(1);
        assertThat(info.metricGroups()).hasSize(5);
        assertThat(info.lines()).containsExactlyElementsOf(lines);
    }

    @Test
    void parsesSectionWithoutMetrics() {
        List<String> lines = List.of(
                "_java_thread_list=0x0000600003b81f00, length=11, elements={",
                "0x0000000146812200, 0x0000000146009200, 0x000000014600e000, 0x0000000146015a00,",
                "0x0000000146016000, 0x0000000146016600, 0x0000000146016c00, 0x0000000146017200,",
                "0x0000000146017800, 0x00000001250ac800, 0x0000000146a9ea00",
                "}"
        );

        SmrInfo info = SmrInfo.fromLines("Threads class SMR info:", lines);

        assertThat(info.javaThreadList()).isNotNull();
        assertThat(info.javaThreadList().addresses()).hasSize(11);
        assertThat(info.listBlocks()).hasSize(1);
        assertThat(info.metricGroups()).isEmpty();
        assertThat(info.lines()).containsExactlyElementsOf(lines);
    }

    @Test
    void parsesSectionWithAdditionalToDeleteListBlock() {
        List<String> lines = List.of(
                "_java_thread_list=0x0000600002c56b60, length=13, elements={",
                "0x0000000142008c00, 0x000000014200d200, 0x0000000121008200, 0x0000000142811e00,",
                "0x0000000142814600, 0x0000000142814c00, 0x000000014280d200, 0x000000014281d200,",
                "0x000000014384ae00, 0x0000000143afc000, 0x00000001278cda00, 0x000000014397a000,",
                "0x0000000143b71e00",
                "}",
                "_to_delete_list=0x0000600002c56cc0, length=14, elements={",
                "0x0000000142008c00, 0x000000014200d200, 0x0000000121008200, 0x0000000142811e00,",
                "0x0000000142814600, 0x0000000142814c00, 0x000000014280d200, 0x000000014281d200,",
                "0x000000014384ae00, 0x0000000143afc000, 0x00000001278cda00, 0x000000014397a000,",
                "0x0000000143b71800, 0x0000000143b71e00",
                "}"
        );

        SmrInfo info = SmrInfo.fromLines("Threads class SMR info:", lines);

        assertThat(info.listBlocks()).hasSize(2);
        assertThat(info.listBlocks().get(1).listName()).isEqualTo("_to_delete_list");
        assertThat(info.listBlocks().get(1).length()).isEqualTo(14);
        assertThat(info.listBlocks().get(1).addresses()).hasSize(14);
        assertThat(info.lines()).containsExactlyElementsOf(lines);
    }
}
