package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The SUMMARY section: banner, command line, host info, time.
 *
 * <p>Preserves exact banner text and blank lines between fields for
 * faithful round-trip reproduction.
 */
public class Summary {

    /** Banner line, e.g. "---------------  S U M M A R Y ------------" */
    private final @NotNull String banner;

    /** Lines between banner and Command Line (typically one blank line) */
    private final @NotNull List<String> preLines;

    /** Command line arguments (value after "Command Line: " prefix) */
    private final @Nullable String commandLine;

    /** Lines between Command Line and Host (typically one blank line) */
    private final @NotNull List<String> midLines;

    /** Host info (value after "Host: " prefix) */
    private final @Nullable String host;

    /** Time info (value after "Time: " prefix) */
    private final @Nullable String time;

    /** Lines after Time before next section banner (typically one blank line) */
    private final @NotNull List<String> postLines;

    @JsonCreator
    public Summary(@JsonProperty("banner") @NotNull String banner,
                   @JsonProperty("preLines") @NotNull List<String> preLines,
                   @JsonProperty("commandLine") @Nullable String commandLine,
                   @JsonProperty("midLines") @NotNull List<String> midLines,
                   @JsonProperty("host") @Nullable String host,
                   @JsonProperty("time") @Nullable String time,
                   @JsonProperty("postLines") @NotNull List<String> postLines) {
        this.banner = banner;
        this.preLines = List.copyOf(preLines);
        this.commandLine = commandLine;
        this.midLines = List.copyOf(midLines);
        this.host = host;
        this.time = time;
        this.postLines = List.copyOf(postLines);
    }

    @JsonProperty public @NotNull String banner() { return banner; }
    @JsonProperty public @NotNull List<String> preLines() { return preLines; }
    @JsonProperty public @Nullable String commandLine() { return commandLine; }
    @JsonProperty public @NotNull List<String> midLines() { return midLines; }
    @JsonProperty public @Nullable String host() { return host; }
    @JsonProperty public @Nullable String time() { return time; }
    @JsonProperty public @NotNull List<String> postLines() { return postLines; }

    @JsonIgnore public @Nullable String hostname() {
        if (host == null || host.startsWith("\"")) return null;
        int comma = host.indexOf(',');
        if (comma <= 0) return null;
        String candidate = host.substring(0, comma).trim();
        // CPU model strings are not hostnames
        if (looksLikeCpuModel(candidate)) return null;
        return candidate;
    }

    private static boolean looksLikeCpuModel(String s) {
        String lower = s.toLowerCase();
        return lower.contains("intel") || lower.contains("amd") || lower.contains("cpu")
                || lower.contains("ghz") || lower.contains("mhz") || lower.contains("core")
                || lower.contains("xeon") || lower.contains("epyc") || lower.contains("ryzen")
                || lower.contains("opteron") || lower.contains("threadripper")
                || lower.contains("apple m") || lower.contains("cortex")
                || lower.contains("neoverse") || lower.contains("graviton")
                || s.contains("@");
    }

    public void accept(HsErrVisitor v) { v.visitSummary(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(banner).append('\n');
        for (String l : preLines) sb.append(l).append('\n');
        if (commandLine != null) sb.append("Command Line: ").append(commandLine).append('\n');
        for (String l : midLines) sb.append(l).append('\n');
        if (host != null) sb.append("Host: ").append(host).append('\n');
        if (time != null) sb.append("Time: ").append(time).append('\n');
        for (String l : postLines) sb.append(l).append('\n');
        return sb.toString();
    }
}
