package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VmMutexInfoTest {

    @Test
    void parsesNoneVariant() {
        VmMutexInfo info = VmMutexInfo.fromLines(
                "VM Mutex/Monitor currently owned by a thread: None",
                List.of()
        );

        assertThat(info.name()).isEqualTo("VM Mutex/Monitor currently owned by a thread: ");
        assertThat(info.ownerState()).isEqualTo("None");
        assertThat(info.entries()).isEmpty();
        assertThat(info.lines()).isEmpty();
    }

    @Test
    void parsesOwnedVariantAndReconstructsLines() {
        String header = "VM Mutex/Monitor currently owned by a thread:  ([mutex/lock_event])";
        List<String> body = List.of("[0x00007fff93a45c70] Threads_lock - owner thread: 0x00007fff8c27f310");

        VmMutexInfo info = VmMutexInfo.fromLines(header, body);

        assertThat(info.name()).isEqualTo("VM Mutex/Monitor currently owned by a thread:  ");
        assertThat(info.ownerState()).isEqualTo("([mutex/lock_event])");
        assertThat(info.entries()).hasSize(1);
        assertThat(info.entries().get(0).address()).isEqualTo("[0x00007fff93a45c70]");
        assertThat(info.entries().get(0).description()).isEqualTo("Threads_lock - owner thread: 0x00007fff8c27f310");
        assertThat(info.lines()).containsExactlyElementsOf(body);
    }
}
