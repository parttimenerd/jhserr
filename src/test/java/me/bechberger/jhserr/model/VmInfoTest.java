package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VmInfoTest {

    @Test
    void parsesLineWithBuilder() {
        String line = "vm_info: OpenJDK 64-Bit Server VM (slowdebug 22-internal-adhoc.testuser.jdk) for bsd-aarch64 JRE (22-internal-adhoc.testuser.jdk), built on 2023-07-24T11:54:15Z by \"testuser\" with clang Apple LLVM 14.0.3 (clang-1403.0.22.14.1)";

        VmInfo info = VmInfo.fromLine(line);

        assertThat(info.line()).isEqualTo(line);
        assertThat(info.vmName()).isEqualTo("OpenJDK 64-Bit Server VM");
        assertThat(info.platform()).isEqualTo("bsd-aarch64");
        assertThat(info.builtBy()).isEqualTo("testuser");
        assertThat(info.vmVersion()).contains("slowdebug 22-internal-adhoc.testuser.jdk");
    }

    @Test
    void parsesLineWithoutBuilder() {
        String line = "vm_info: OpenJDK 64-Bit Server VM (25+36-LTS) for bsd-aarch64 JRE (25+36-LTS), built on 2025-09-15T12:55:24Z with clang Apple LLVM 15.0.0 (clang-1500.3.9.4)";

        VmInfo info = VmInfo.fromLine(line);

        assertThat(info.line()).isEqualTo(line);
        assertThat(info.vmName()).isEqualTo("OpenJDK 64-Bit Server VM");
        assertThat(info.platform()).isEqualTo("bsd-aarch64");
        assertThat(info.builtBy()).isNull();
        assertThat(info.vmVersion()).contains("25+36-LTS");
    }
}
