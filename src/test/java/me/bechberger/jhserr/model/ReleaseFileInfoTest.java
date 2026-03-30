package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseFileInfoTest {

    @Test
    void parsesQuotedEntriesAndReconstructs() {
        List<String> lines = List.of(
                "IMPLEMENTOR=\"SAP SE\"",
                "JAVA_VERSION=\"25\"",
                "SOURCE=\".:git:93b9b175c830\""
        );

        ReleaseFileInfo info = ReleaseFileInfo.fromLines("Release file:", lines);

        assertThat(info.entries()).hasSize(3);
        assertThat(info.entries().get(0).key()).isEqualTo("IMPLEMENTOR");
        assertThat(info.entries().get(0).quoted()).isTrue();
        assertThat(info.entries().get(0).value()).isEqualTo("SAP SE");

        assertThat(info.lines()).containsExactlyElementsOf(lines);
    }

    @Test
    void parsesUnquotedAndNoEqualsEntries() {
        List<String> lines = List.of(
                "UNQUOTED=default",
                "BROKEN_LINE"
        );

        ReleaseFileInfo info = ReleaseFileInfo.fromLines("Release file:", lines);

        assertThat(info.entries()).hasSize(2);
        assertThat(info.entries().get(0).hasEquals()).isTrue();
        assertThat(info.entries().get(0).quoted()).isFalse();
        assertThat(info.entries().get(0).value()).isEqualTo("default");

        assertThat(info.entries().get(1).hasEquals()).isFalse();
        assertThat(info.entries().get(1).key()).isEqualTo("BROKEN_LINE");

        assertThat(info.lines()).containsExactlyElementsOf(lines);
    }
}
