package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigTest {

    @Test
    void parsesAndReconstructsConfiguredOutputs() {
        List<String> lines = List.of(
                "Log output configuration:",
                " #0: stdout all=warning uptime,level,tags foldmultilines=false",
                " #1: stderr all=off uptime,level,tags foldmultilines=false"
        );

        LoggingConfig config = LoggingConfig.fromLines("Logging:", lines);

        assertThat(config.name()).isEqualTo("Logging:");
        assertThat(config.entries()).hasSize(3);
        assertThat(config.entries().get(0)).isInstanceOf(LoggingConfig.RawLine.class);
        assertThat(config.entries().get(1)).isInstanceOf(LoggingConfig.OutputEntry.class);
        LoggingConfig.OutputEntry firstOutput = (LoggingConfig.OutputEntry) config.entries().get(1);
        assertThat(firstOutput.index()).isEqualTo(0);
        assertThat(firstOutput.target()).isEqualTo("stdout");
        assertThat(firstOutput.config()).isEqualTo("all=warning uptime,level,tags foldmultilines=false");
        assertThat(config.lines()).containsExactlyElementsOf(lines);
        assertThat(config.toString()).isEqualTo("""
                Logging:
                Log output configuration:
                 #0: stdout all=warning uptime,level,tags foldmultilines=false
                 #1: stderr all=off uptime,level,tags foldmultilines=false
                """);
    }

    @Test
    void preservesUnstructuredLinesAsRawEntries() {
        List<String> lines = List.of(
                "Log output configuration:",
                "#not-an-index entry"
        );

        LoggingConfig config = LoggingConfig.fromLines("Logging:", lines);

        assertThat(config.entries()).hasSize(2);
        assertThat(config.entries().get(1)).isInstanceOf(LoggingConfig.RawLine.class);
        assertThat(config.lines()).containsExactlyElementsOf(lines);
    }
}
