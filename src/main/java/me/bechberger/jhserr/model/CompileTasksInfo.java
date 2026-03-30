package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.bechberger.jhserr.HsErrVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Active compile tasks from the PROCESS section.
 *
 * <p>Header: {@code Threads with active compile tasks:}
 */
public record CompileTasksInfo(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("tasks") @NotNull List<CompileTask> tasks,
        @JsonProperty("total") @Nullable Integer total
) implements ProcessSectionItem {

    private static final Pattern HEADER_ONLY = Pattern.compile(
            "^(C\\d)\\s+(CompilerThread\\S+)(\\s+)(\\d+)(\\s+)(\\d+)(\\s[%!bsn ]+)(\\d+)(\\s*)$");
    private static final Pattern HEADER_WITH_METHOD = Pattern.compile(
            "^(C\\d)\\s+(CompilerThread\\S+)(\\s+)(\\d+)(\\s+)(\\d+)(\\s[%!bsn ]+)(\\d+)(\\s+)(\\S.*)$");
    /** Two-number variant (no tier): compiler thread elapsed compileId [flags] method */
    private static final Pattern TWO_NUM_WITH_METHOD = Pattern.compile(
            "^(C\\d)\\s+(CompilerThread\\S+)(\\s+)(\\d+)(\\s+)(\\d+)(\\s[%!bsn ]+)(\\S.*)$");
    private static final Pattern METHOD_CONT = Pattern.compile("^(\\s+)(.+)$");
    private static final Pattern TOTAL = Pattern.compile("^Total:\\s+(\\d+)$");

    @JsonCreator
    public CompileTasksInfo(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("tasks") @Nullable List<CompileTask> tasks,
            @JsonProperty("total") @Nullable Integer total) {
        this.name = name;
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        this.total = total;
    }

    /** Parser-compatible constructor. */
    public CompileTasksInfo(@NotNull String name, @NotNull List<String> lines) {
        this(parse(name, lines));
    }

    private CompileTasksInfo(@NotNull Parsed parsed) {
        this(parsed.name, parsed.tasks, parsed.total);
    }

    @Override
    public @NotNull String toString() {
        var sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines()) sb.append(line).append('\n');
        return sb.toString();
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> out = new ArrayList<>();
        for (CompileTask task : tasks) {
            out.add(task.primaryLine());
            if (task.methodOnContinuation()) {
                out.add(task.methodIndent() + task.method());
            }
        }
        if (total != null) {
            out.add("Total: " + total);
        }
        return out;
    }

    @Override
    public void accept(HsErrVisitor v) {
        v.visitCompileTasksInfo(this);
    }

    private static Parsed parse(@NotNull String name, @NotNull List<String> lines) {
        List<CompileTask> tasks = new ArrayList<>();
        Integer total = null;

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);

            Matcher tm = TOTAL.matcher(line);
            if (tm.matches()) {
                total = parseInt(tm.group(1));
                i++;
                continue;
            }

            Matcher hw = HEADER_WITH_METHOD.matcher(line);
            if (hw.matches()) {
                tasks.add(new CompileTask(
                        hw.group(1), hw.group(2),
                        parseInt(hw.group(4)), parseInt(hw.group(6)), parseInt(hw.group(8)),
                        hw.group(3), hw.group(5), hw.group(7),
                        hw.group(9), "", hw.group(10), false
                ));
                i++;
                continue;
            }

            Matcher ho = HEADER_ONLY.matcher(line);
            if (ho.matches()) {
                String compiler = ho.group(1);
                String thread = ho.group(2);
                int compileId = parseInt(ho.group(4));
                int bci = parseInt(ho.group(6));
                int level = parseInt(ho.group(8));
                String s1 = ho.group(3);
                String s2 = ho.group(5);
                String s3 = ho.group(7);
                String trailing = ho.group(9);

                String methodIndent = "     ";
                String method = "";
                if (i + 1 < lines.size()) {
                    Matcher cont = METHOD_CONT.matcher(lines.get(i + 1));
                    if (cont.matches() && !TOTAL.matcher(lines.get(i + 1)).matches()) {
                        methodIndent = cont.group(1);
                        method = cont.group(2);
                        tasks.add(new CompileTask(
                                compiler, thread, compileId, bci, level,
                                s1, s2, s3, trailing, methodIndent, method, true
                        ));
                        i += 2;
                        continue;
                    }
                }

                tasks.add(new CompileTask(
                        compiler, thread, compileId, bci, level,
                        s1, s2, s3, trailing, methodIndent, method, false
                ));
                i++;
                continue;
            }

            // Two-number variant (no tier): compiler thread elapsed compileId [flags] method
            Matcher tw = TWO_NUM_WITH_METHOD.matcher(line);
            if (tw.matches()) {
                tasks.add(new CompileTask(
                        tw.group(1), tw.group(2),
                        parseInt(tw.group(4)), parseInt(tw.group(6)), -1,
                        tw.group(3), tw.group(5), tw.group(7),
                        "", "", tw.group(8), false
                ));
                i++;
                continue;
            }

            i++;
        }

        return new Parsed(name, tasks, total);
    }

    private static int parseInt(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record Parsed(String name, List<CompileTask> tasks, Integer total) {}

    public record CompileTask(
            @JsonProperty("compiler") @NotNull String compiler,
            @JsonProperty("thread") @NotNull String thread,
            @JsonProperty("compileId") int compileId,
            @JsonProperty("bci") int bci,
            @JsonProperty("level") int level,
            @JsonProperty("spaceAfterThread") @NotNull String spaceAfterThread,
            @JsonProperty("spaceAfterCompileId") @NotNull String spaceAfterCompileId,
            @JsonProperty("spaceAfterBci") @NotNull String spaceAfterBci,
            @JsonProperty("trailingSpace") @NotNull String trailingSpace,
            @JsonProperty("methodIndent") @NotNull String methodIndent,
            @JsonProperty("method") @NotNull String method,
            @JsonProperty("methodOnContinuation") boolean methodOnContinuation
    ) {
        public @NotNull String primaryLine() {
            String prefix = compiler + " " + thread + spaceAfterThread + compileId + spaceAfterCompileId + bci + spaceAfterBci;
            if (level == -1) {
                // Two-number variant: no tier, method directly after flags
                return prefix + method;
            }
            if (methodOnContinuation) {
                return prefix + level + trailingSpace;
            }
            return prefix + level +
                    (method.isEmpty() ? trailingSpace : trailingSpace + methodIndent + method);
        }
    }
}
