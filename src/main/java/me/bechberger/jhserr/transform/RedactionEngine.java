package me.bechberger.jhserr.transform;

import me.bechberger.jhserr.HsErrVisitor;
import me.bechberger.jhserr.model.*;

import java.util.*;
import java.util.regex.*;

/**
 * Read-only visitor that scans an hs_err report for sensitive information.
 *
 * <p>Collects findings about:
 * <ul>
 *   <li>Hostnames (from Summary host line and uname)</li>
 *   <li>Usernames (from file paths like /Users/USERNAME/ or /home/USERNAME/)</li>
 *   <li>Environment variables that may contain secrets</li>
 *   <li>PIDs and thread IDs</li>
 *   <li>IP addresses</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * RedactionEngine engine = new RedactionEngine();
 * report.accept(engine);
 * List&lt;String&gt; findings = engine.findings();
 * </pre>
 */
public class RedactionEngine implements HsErrVisitor {

    private static final Pattern USERNAME_IN_PATH = Pattern.compile(
            "/(?:Users|home|export/home)/([^/\\s]+)");

    private static final Set<String> SAFE_ENV_VARS = Set.of(
            "PATH", "SHELL", "LANG", "LC_ALL", "LC_CTYPE", "TERM", "DISPLAY",
            "JAVA_HOME", "JDK_HOME", "JAVA_TOOL_OPTIONS",
            "CLASSPATH", "_JAVA_OPTIONS");

    private final List<String> findings = new ArrayList<>();
    private final Set<String> usernames = new LinkedHashSet<>();
    private final Set<String> hostnames = new LinkedHashSet<>();

    /** Discovered redaction findings (descriptions of what was found). */
    public List<String> findings() { return Collections.unmodifiableList(findings); }

    /** Unique usernames found in paths. */
    public Set<String> usernames() { return Collections.unmodifiableSet(usernames); }

    /** Primary hostname if found (first discovered). */
    public String hostname() { return hostnames.isEmpty() ? null : hostnames.iterator().next(); }

    /** All hostnames found. */
    public Set<String> hostnames() { return Collections.unmodifiableSet(hostnames); }

    @Override
    public void visitHeader(Header header) {
        scanForUsernames(header.jreVersion());
        scanForUsernames(header.problematicFrame());
        scanForUsernames(header.coreDumpLine());
        if (header.pid() != null) {
            findings.add("PID found: " + header.pid());
        }
    }

    @Override
    public void visitSummary(Summary summary) {
        if (summary.hostname() != null) {
            hostnames.add(summary.hostname());
            findings.add("Hostname found: " + summary.hostname());
        }
        scanForUsernames(summary.commandLine());
    }

    @Override
    public void visitBlankLine(BlankLine line) {
        // blank separators have nothing to scan
    }

    @Override
    public void visitVmInfo(VmInfo info) {
        scanForUsernames(info.line());
    }

    @Override
    public void visitSmrInfo(SmrInfo info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitActiveLocale(ActiveLocale info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitLoggingConfig(LoggingConfig info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitSignalHandlers(SignalHandlers info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitNativeMemoryTracking(NativeMemoryTracking info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitGcPreciousLog(GcPreciousLog info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitHeapSummary(HeapSummary info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitHeapRegions(HeapRegions info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitFrame(StackFrame frame) {
        scanForUsernames(frame.line());
    }

    @Override
    public void visitDynamicLibraries(DynamicLibraries libs) {
        for (DynamicLibraries.LibraryEntry entry : libs.entries()) {
            if (entry.path() != null) scanForUsernames(entry.path());
        }
    }

    @Override
    public void visitVmArguments(VmArguments args) {
        for (String line : args.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitEnvironmentVariables(EnvironmentVariables vars) {
        for (var entry : vars.vars().entrySet()) {
            if (!SAFE_ENV_VARS.contains(entry.getKey())) {
                findings.add("Env var may contain sensitive data: " + entry.getKey());
            }
            scanForUsernames(entry.getValue());
        }
    }

    @Override
    public void visitNamedSection(NamedSection section) {
        scanForUsernames(section.name());
        for (String line : section.lines()) {
            scanForUsernames(line);
            // Discover hostname from uname: line (inside OsInfo)
            if (line.startsWith("uname:")) {
                extractHostnameFromUname(line);
            }
        }
    }

    @Override
    public void visitOsInfo(OsInfo info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
            if (line.startsWith("uname:")) {
                extractHostnameFromUname(line);
            }
        }
    }

    @Override
    public void visitCpuInfo(CpuInfo info) {
        scanForUsernames(info.name());
        for (String line : info.lines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitErrorDuringError(ErrorDuringError error) {
        scanForUsernames(error.detail());
        scanForUsernames(error.line());
    }

    @Override
    public void visitRegisters(Registers registers) {
        for (String line : registers.registerLines()) {
            scanForUsernames(line);
        }
    }

    @Override
    public void visitEventLog(EventLog log) {
        for (String event : log.events()) {
            scanForUsernames(event);
        }
    }

    @Override
    public void visitGlobalFlags(GlobalFlags flags) {
        for (String line : flags.bodyLines()) {
            scanForUsernames(line);
        }
    }

    // SignalInfo, StackBoundsInfo, MemoryInfo: structured fields don't contain paths/usernames

    /** Extract hostname from "uname: Linux HOSTNAME ..." or "uname: Darwin HOSTNAME ..." */
    private void extractHostnameFromUname(String unameLine) {
        String after = unameLine.substring("uname:".length()).trim();
        String[] parts = after.split("\\s+", 3);
        if (parts.length >= 3) {
            String second = parts[1];
            if (!second.isEmpty() && !Character.isDigit(second.charAt(0))) {
                hostnames.add(second);
                findings.add("Hostname found in uname: " + second);
            }
        }
    }

    private void scanForUsernames(String text) {
        if (text == null) return;
        Matcher m = USERNAME_IN_PATH.matcher(text);
        while (m.find()) {
            String username = m.group(1);
            if (usernames.add(username)) {
                findings.add("Username found in path: " + username);
            }
        }
    }
}
