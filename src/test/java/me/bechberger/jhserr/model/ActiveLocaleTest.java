package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveLocaleTest {

    @Test
    void parsesAndReconstructsLocaleEntries() {
        List<String> lines = List.of(
                "LC_ALL=en_US.UTF-8",
                "LC_COLLATE=en_US.UTF-8",
                "LC_CTYPE=en_US.UTF-8",
                "LC_MESSAGES=en_US.UTF-8",
                "LC_MONETARY=en_US.UTF-8",
                "LC_NUMERIC=en_US.UTF-8",
                "LC_TIME=en_US.UTF-8"
        );

        ActiveLocale info = ActiveLocale.fromLines("Active Locale:", lines);

        assertThat(info.name()).isEqualTo("Active Locale:");
        assertThat(info.entries()).hasSize(7);
        assertThat(info.entries().get(0).key()).isEqualTo("LC_ALL");
        assertThat(info.entries().get(0).separator()).isEqualTo("=");
        assertThat(info.entries().get(0).value()).isEqualTo("en_US.UTF-8");
        assertThat(info.lines()).containsExactlyElementsOf(lines);
        assertThat(info.toString()).isEqualTo("""
                Active Locale:
                LC_ALL=en_US.UTF-8
                LC_COLLATE=en_US.UTF-8
                LC_CTYPE=en_US.UTF-8
                LC_MESSAGES=en_US.UTF-8
                LC_MONETARY=en_US.UTF-8
                LC_NUMERIC=en_US.UTF-8
                LC_TIME=en_US.UTF-8
                """);
    }

    @Test
    void supportsEntriesWithoutEqualsSign() {
        List<String> lines = List.of("C", "LC_ALL=en_US.UTF-8");

        ActiveLocale info = ActiveLocale.fromLines("Active Locale:", lines);

        assertThat(info.entries().get(0).key()).isEqualTo("C");
        assertThat(info.entries().get(0).separator()).isEmpty();
        assertThat(info.entries().get(0).value()).isEmpty();
        assertThat(info.lines()).containsExactlyElementsOf(lines);
    }
}
