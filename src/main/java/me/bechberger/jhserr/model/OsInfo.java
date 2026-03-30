package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.*;

import me.bechberger.jhserr.HsErrVisitor;

/**
 * Parsed OS: sub-section from the SYSTEM section.
 *
 * <p>Extends {@link NamedSection} for round-trip storage (header + body lines),
 * but all well-known fields are parsed eagerly at construction time — there are
 * no lazy regex scans.
 *
 * <p>macOS example:
 * <pre>
 * OS:
 * uname: Darwin MYHOST 22.6.0 Darwin Kernel Version 22.6.0: ... arm64
 * OS uptime: 3 days 22:33 hours
 * rlimit (soft/hard): STACK 8176k/65520k , CORE 0k/infinity , ...
 * load average: 9.25 11.63 11.44
 * </pre>
 *
 * <p>Linux has additional fields: distro info (NAME=, VERSION=, etc.),
 * libc version, Open File Descriptors, /proc/meminfo, container info, etc.
 */
public non-sealed class OsInfo implements SystemSectionItem {

    private final @NotNull String name;
    private final @NotNull List<String> lines;

    // Eagerly parsed fields
    private final @Nullable String uname;
    private final @Nullable String uptime;
    private final @Nullable String rlimit;
    private final @Nullable String loadAverage;
    private final @Nullable String libc;
    private final @Nullable String prettyName;
    private final @Nullable String distroId;
    private final @Nullable Integer openFileDescriptors;

    private static final Pattern UNAME_PAT = Pattern.compile("^uname:\\s+(.+)$");
    private static final Pattern UPTIME_PAT = Pattern.compile("^OS uptime:\\s+(.+)$");
    private static final Pattern RLIMIT_PAT = Pattern.compile("^rlimit \\(soft/hard\\):\\s+(.+)$");
    private static final Pattern LOAD_PAT = Pattern.compile("^load average:\\s+(.+)$");
    private static final Pattern LIBC_PAT = Pattern.compile("^libc:\\s+(.+)$");
    private static final Pattern PRETTY_NAME_PAT = Pattern.compile("^PRETTY_NAME=\"(.+)\"$");
    private static final Pattern ID_PAT = Pattern.compile("^ID=\"?([^\"]+)\"?$");
    private static final Pattern OPEN_FD_PAT = Pattern.compile("^Open File Descriptors:\\s+(\\d+)$");

    @JsonCreator
    public OsInfo(@JsonProperty("name") @NotNull String name,
                  @JsonProperty("lines") @NotNull List<String> lines) {
        this.name = Objects.requireNonNull(name, "name");
        this.lines = lines != null ? List.copyOf(lines) : List.of();
        // Parse all known fields eagerly
        String uname = null, uptime = null, rlimit = null, loadAvg = null;
        String libc = null, prettyName = null, distroId = null;
        Integer openFd = null;
        for (String line : lines) {
            Matcher m;
            if (uname == null && (m = UNAME_PAT.matcher(line)).find()) uname = m.group(1).strip();
            else if (uptime == null && (m = UPTIME_PAT.matcher(line)).find()) uptime = m.group(1).strip();
            else if (rlimit == null && (m = RLIMIT_PAT.matcher(line)).find()) rlimit = m.group(1).strip();
            else if (loadAvg == null && (m = LOAD_PAT.matcher(line)).find()) loadAvg = m.group(1).strip();
            else if (libc == null && (m = LIBC_PAT.matcher(line)).find()) libc = m.group(1).strip();
            else if (prettyName == null && (m = PRETTY_NAME_PAT.matcher(line)).find()) prettyName = m.group(1);
            else if (distroId == null && (m = ID_PAT.matcher(line)).find()) distroId = m.group(1);
            else if (openFd == null && (m = OPEN_FD_PAT.matcher(line)).find()) openFd = Integer.parseInt(m.group(1));
        }
        this.uname = uname;
        this.uptime = uptime;
        this.rlimit = rlimit;
        this.loadAverage = loadAvg;
        this.libc = libc;
        this.prettyName = prettyName;
        this.distroId = distroId;
        this.openFileDescriptors = openFd;
    }

    /** Full uname string, e.g. "Darwin MYHOST 22.6.0 ..." or "Linux host 6.4.0-..." */
    @JsonIgnore public @Nullable String uname() { return uname; }

    /** OS uptime, e.g. "3 days 22:33 hours" */
    @JsonIgnore public @Nullable String uptime() { return uptime; }

    /** rlimit values, e.g. "STACK 8176k/65520k , CORE 0k/infinity , ..." */
    @JsonIgnore public @Nullable String rlimit() { return rlimit; }

    /** Load average, e.g. "9.25 11.63 11.44" */
    @JsonIgnore public @Nullable String loadAverage() { return loadAverage; }

    /** libc version (Linux), e.g. "glibc 2.38 NPTL 2.38" */
    @JsonIgnore public @Nullable String libc() { return libc; }

    /** PRETTY_NAME from os-release (Linux), e.g. "SUSE Linux Enterprise Server 15 SP6" */
    @JsonIgnore public @Nullable String prettyName() { return prettyName; }

    /** ID from os-release (Linux), e.g. "sles", "ubuntu" */
    @JsonIgnore public @Nullable String distroId() { return distroId; }

    /** Open file descriptor count (Linux) */
    @JsonIgnore public @Nullable Integer openFileDescriptors() { return openFileDescriptors; }

    /** True if uname indicates Linux */
    @JsonIgnore public boolean isLinux() { return uname != null && uname.startsWith("Linux "); }

    /** True if uname indicates macOS / Darwin */
    @JsonIgnore public boolean isDarwin() { return uname != null && uname.startsWith("Darwin "); }

    /** Kernel name from uname: "Linux" or "Darwin" */
    @JsonIgnore public @Nullable String kernelName() {
        if (uname == null) return null;
        int sp = uname.indexOf(' ');
        return sp > 0 ? uname.substring(0, sp) : uname;
    }

    /** Hostname from uname (second field: "Linux HOSTNAME ..." or "Darwin HOSTNAME ...") */
    @JsonIgnore public @Nullable String unameHostname() {
        if (uname == null) return null;
        String[] parts = uname.split("\\s+", 3);
        if (parts.length >= 2) {
            String second = parts[1];
            if (!second.isEmpty() && !Character.isDigit(second.charAt(0))) return second;
        }
        return null;
    }

    @JsonProperty public @NotNull String name() { return name; }
    @JsonProperty public @NotNull List<String> lines() { return lines; }

    @Override
    public void accept(HsErrVisitor v) { v.visitOsInfo(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines) sb.append(line).append('\n');
        return sb.toString();
    }
}
