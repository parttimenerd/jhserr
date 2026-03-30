package me.bechberger.jhserr.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompileTasksInfoTest {

    @Test
    void parsesTwoLineTaskAndTotal() {
        String name = "Threads with active compile tasks:";
        List<String> body = List.of(
                "C2 CompilerThread0     1447   89       4       ",
                "     math.Lexer::parseDigit (68 bytes)",
                "Total: 1"
        );

        CompileTasksInfo info = new CompileTasksInfo(name, body);

        assertThat(info.tasks()).hasSize(1);
        var task = info.tasks().get(0);
        assertThat(task.compiler()).isEqualTo("C2");
        assertThat(task.thread()).isEqualTo("CompilerThread0");
        assertThat(task.compileId()).isEqualTo(1447);
        assertThat(task.bci()).isEqualTo(89);
        assertThat(task.level()).isEqualTo(4);
        assertThat(task.method()).isEqualTo("math.Lexer::parseDigit (68 bytes)");
        assertThat(task.methodOnContinuation()).isTrue();
        assertThat(info.total()).isEqualTo(1);

        assertThat(info.lines()).containsExactlyElementsOf(body);
    }

    @Test
    void parsesOneLineTaskWithoutTotal() {
        String name = "Threads with active compile tasks:";
        List<String> body = List.of(
                "C2 CompilerThread0     1017 1971       4       javassist.bytecode.MethodInfo::prune (238 bytes)"
        );

        CompileTasksInfo info = new CompileTasksInfo(name, body);

        assertThat(info.tasks()).hasSize(1);
        var task = info.tasks().get(0);
        assertThat(task.compileId()).isEqualTo(1017);
        assertThat(task.bci()).isEqualTo(1971);
        assertThat(task.level()).isEqualTo(4);
        assertThat(task.method()).isEqualTo("javassist.bytecode.MethodInfo::prune (238 bytes)");
        assertThat(task.methodOnContinuation()).isFalse();
        assertThat(info.total()).isNull();

        assertThat(info.lines()).containsExactlyElementsOf(body);
    }

    @Test
    void parsesEmptySection() {
        CompileTasksInfo info = new CompileTasksInfo("Threads with active compile tasks:", List.of());

        assertThat(info.tasks()).isEmpty();
        assertThat(info.total()).isNull();
        assertThat(info.lines()).isEmpty();
    }
}
