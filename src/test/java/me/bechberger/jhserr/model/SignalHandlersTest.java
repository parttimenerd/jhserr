package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalHandlersTest {

    @Test
    void parsesAndReconstructsSignalHandlerEntries() {
        List<String> lines = List.of(
                "   SIGSEGV: crash_handler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked",
                "   SIGINT: SIG_IGN, mask=00000000000000000000000000000000, flags=none, unblocked"
        );

        SignalHandlers handlers = SignalHandlers.fromLines("Signal Handlers:", lines);

        assertThat(handlers.name()).isEqualTo("Signal Handlers:");
        assertThat(handlers.entries()).hasSize(2);
        assertThat(handlers.entries().get(0)).isInstanceOf(SignalHandlers.SignalHandlerEntry.class);
        SignalHandlers.SignalHandlerEntry sigsegv =
                (SignalHandlers.SignalHandlerEntry) handlers.entries().get(0);
        assertThat(sigsegv.leadingWhitespace()).isEqualTo("   ");
        assertThat(sigsegv.signalName()).isEqualTo("SIGSEGV");
        assertThat(sigsegv.separator()).isEqualTo(":");
        assertThat(sigsegv.details())
                .isEqualTo(" crash_handler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked");
        assertThat(handlers.lines()).containsExactlyElementsOf(lines);
        assertThat(handlers.toString()).isEqualTo("""
                Signal Handlers:
                   SIGSEGV: crash_handler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked
                   SIGINT: SIG_IGN, mask=00000000000000000000000000000000, flags=none, unblocked
                """);
    }

    @Test
    void preservesUnstructuredLinesAsRawEntries() {
        List<String> lines = List.of(
                "SIGBROKEN no-colon"
        );

        SignalHandlers handlers = SignalHandlers.fromLines("Signal Handlers:", lines);

                assertThat(handlers.entries()).hasSize(1);
                assertThat(handlers.entries().get(0)).isInstanceOf(SignalHandlers.RawLine.class);
        assertThat(handlers.lines()).containsExactlyElementsOf(lines);
    }
}
