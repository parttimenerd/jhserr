package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The '#'-prefixed header preamble before the first section banner.
 *
 * <p>Produced by the HotSpot VM error handler ({@code vmError.cpp}).
 * The exact output sequence is:
 * <ol>
 *   <li>{@code # A fatal error has been detected ...}</li>
 *   <li>{@code #  <errorDetail>} (signal/error with pc, pid, tid)</li>
 *   <li>(optional) {@code #  <assertDetail>}</li>
 *   <li>{@code # JRE version: ...} and {@code # Java VM: ...}</li>
 *   <li>{@code # Problematic frame:} + frame line</li>
 *   <li>{@code # <coreDumpLine>}</li>
 *   <li>(optional) {@code # <jfrFileLine>}</li>
 *   <li>{@code # If you would like to submit a bug report ...} + URL</li>
 *   <li>(optional) crash-outside-JVM notice</li>
 * </ol>
 */
@JsonDeserialize(builder = Header.Builder.class)
public class Header {

    /** Signal or error type: "SIGSEGV", "SIGBUS", "Internal Error", "Out of Memory Error" */
    private final @NotNull String errorType;

    /** Full error detail, e.g. "SIGSEGV (0xb) at pc=0x..., pid=16540, tid=8451" */
    private final @NotNull String errorDetail;

    /** Assert/guarantee message, e.g. "assert(...) failed: ..." */
    private final @Nullable String assertDetail;

    /** JRE version after "JRE version: " prefix */
    private final @Nullable String jreVersion;

    /** Java VM info after "Java VM: " prefix */
    private final @Nullable String javaVm;

    /** Problematic frame (without "# " prefix), e.g. "C  [lib.so+0xf588]  handler+0x464" */
    private final @Nullable String problematicFrame;

    /** Process ID extracted from errorDetail */
    private final @Nullable String pid;

    /** Thread ID extracted from errorDetail */
    private final @Nullable String tid;

    /** Core dump status (without "# " prefix), e.g. "No core dump will be written. ..." */
    private final @Nullable String coreDumpLine;

    /** JFR file info (without "# " prefix), e.g. "JFR recording file will be written. ..." */
    private final @Nullable String jfrFileLine;

    /** Bug report URL, e.g. "https://bugreport.java.com/bugreport/crash.jsp" */
    private final @Nullable String bugReportUrl;

    /** True when the crash was in native code (adds 2 lines after URL) */
    private final boolean crashedOutsideJvm;

    /** Number of blank lines after closing '#' (before next section banner) */
    private final int trailingBlankLines;

    /** Non-'#' diagnostic detail lines that appear after assert/error detail (e.g. Shenandoah diagnostics) */
    private final @NotNull List<String> extraDetailLines;

    private Header(Builder b) {
        this.errorType = Objects.requireNonNull(b.errorType);
        this.errorDetail = Objects.requireNonNull(b.errorDetail);
        this.assertDetail = b.assertDetail;
        this.jreVersion = b.jreVersion;
        this.javaVm = b.javaVm;
        this.problematicFrame = b.problematicFrame;
        this.pid = b.pid;
        this.tid = b.tid;
        this.coreDumpLine = b.coreDumpLine;
        this.jfrFileLine = b.jfrFileLine;
        this.bugReportUrl = b.bugReportUrl;
        this.crashedOutsideJvm = b.crashedOutsideJvm;
        this.trailingBlankLines = b.trailingBlankLines;
        this.extraDetailLines = b.extraDetailLines;
    }

    public static Builder builder() { return new Builder(); }

    /** Creates a builder pre-populated with this header's values. */
    public Builder toBuilder() {
        return new Builder()
            .errorType(errorType).errorDetail(errorDetail).assertDetail(assertDetail)
            .jreVersion(jreVersion).javaVm(javaVm).problematicFrame(problematicFrame)
            .pid(pid).tid(tid).coreDumpLine(coreDumpLine).jfrFileLine(jfrFileLine)
            .bugReportUrl(bugReportUrl).crashedOutsideJvm(crashedOutsideJvm)
            .trailingBlankLines(trailingBlankLines).extraDetailLines(extraDetailLines);
    }

    @JsonProperty public @NotNull String errorType() { return errorType; }
    @JsonProperty public @NotNull String errorDetail() { return errorDetail; }
    @JsonProperty public @Nullable String assertDetail() { return assertDetail; }
    @JsonProperty public @Nullable String jreVersion() { return jreVersion; }
    @JsonProperty public @Nullable String javaVm() { return javaVm; }
    @JsonProperty public @Nullable String problematicFrame() { return problematicFrame; }
    @JsonProperty public @Nullable String pid() { return pid; }
    @JsonProperty public @Nullable String tid() { return tid; }
    @JsonProperty public @Nullable String coreDumpLine() { return coreDumpLine; }
    @JsonProperty public @Nullable String jfrFileLine() { return jfrFileLine; }
    @JsonProperty public @Nullable String bugReportUrl() { return bugReportUrl; }
    @JsonProperty public boolean crashedOutsideJvm() { return crashedOutsideJvm; }
    @JsonProperty public int trailingBlankLines() { return trailingBlankLines; }
    @JsonProperty public @NotNull List<String> extraDetailLines() { return extraDetailLines; }

    public void accept(HsErrVisitor v) { v.visitHeader(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("#\n");
        sb.append("# A fatal error has been detected by the Java Runtime Environment:\n");
        sb.append("#\n");
        sb.append("#  ").append(errorDetail).append('\n');
        if (assertDetail != null) sb.append("#  ").append(assertDetail).append('\n');
        if (!extraDetailLines.isEmpty()) {
            // Only add blank line before if first line is not blank
            if (!extraDetailLines.get(0).isEmpty()) {
                sb.append("\n");
            }
            for (String line : extraDetailLines) sb.append(line).append('\n');
            // Only add blank line after if last line is not blank
            if (!extraDetailLines.get(extraDetailLines.size() - 1).isEmpty()) {
                sb.append("\n");
            }
        }
        sb.append("#\n");
        if (jreVersion != null) sb.append("# JRE version:").append(jreVersion).append('\n');
        if (javaVm != null) sb.append("# Java VM:").append(javaVm).append('\n');
        if (problematicFrame != null) {
            sb.append("# Problematic frame:\n");
            sb.append("# ").append(problematicFrame).append('\n');
            sb.append("#\n");
        }
        if (coreDumpLine != null) {
            sb.append("# ").append(coreDumpLine).append('\n');
            sb.append("#\n");
        }
        if (jfrFileLine != null) {
            sb.append("# ").append(jfrFileLine).append('\n');
            sb.append("#\n");
        }
        if (bugReportUrl != null) {
            sb.append("# If you would like to submit a bug report, please visit:\n");
            sb.append("#   ").append(bugReportUrl).append('\n');
        }
        if (crashedOutsideJvm) {
            sb.append("# The crash happened outside the Java Virtual Machine in native code.\n");
            sb.append("# See problematic frame for where to report the bug.\n");
        }
        sb.append("#\n");
        for (int i = 0; i < trailingBlankLines; i++) sb.append('\n');
        return sb.toString();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String errorType;
        private String errorDetail;
        private String assertDetail;
        private String jreVersion;
        private String javaVm;
        private String problematicFrame;
        private String pid;
        private String tid;
        private String coreDumpLine;
        private String jfrFileLine;
        private String bugReportUrl;
        private boolean crashedOutsideJvm;
        private int trailingBlankLines = 1;
        private @NotNull List<String> extraDetailLines = List.of();

        public Builder errorType(String v) { this.errorType = v; return this; }
        public Builder errorDetail(String v) { this.errorDetail = v; return this; }
        public Builder assertDetail(String v) { this.assertDetail = v; return this; }
        public Builder jreVersion(String v) { this.jreVersion = v; return this; }
        public Builder javaVm(String v) { this.javaVm = v; return this; }
        public Builder problematicFrame(String v) { this.problematicFrame = v; return this; }
        public Builder pid(String v) { this.pid = v; return this; }
        public Builder tid(String v) { this.tid = v; return this; }
        public Builder coreDumpLine(String v) { this.coreDumpLine = v; return this; }
        public Builder jfrFileLine(String v) { this.jfrFileLine = v; return this; }
        public Builder bugReportUrl(String v) { this.bugReportUrl = v; return this; }
        public Builder crashedOutsideJvm(boolean v) { this.crashedOutsideJvm = v; return this; }
        public Builder trailingBlankLines(int v) { this.trailingBlankLines = v; return this; }
        public Builder extraDetailLines(List<String> v) { this.extraDetailLines = v != null ? v : List.of(); return this; }

        public Header build() { return new Header(this); }
    }
}
