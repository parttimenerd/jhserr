package me.bechberger.jhserr.transform;

import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Transformer that produces a redacted copy of an {@link HsErrReport}.
 *
 * <p>Driven by {@link RedactionConfig}: every redaction category can be
 * individually enabled/disabled. Supports sensitive path prefixes,
 * per-category placeholders, path modes, and env var safe-lists.
 *
 * <p>Two-pass: {@link #transform(HsErrReport)} first scans the tree for
 * usernames and hostnames, then rebuilds with replacements applied.
 *
 * <p>Configurable strings (usernames, hostnames, paths) can be supplied
 * through the config or discovered automatically from the report.
 */
public class RedactionTransformer extends HsErrTransformer {

    private static final Pattern USERNAME_IN_PATH = Pattern.compile(
            "/(?:Users|home|export/home)/([^/\\s]+)");

    private final RedactionConfig config;
    private final Set<String> usernames = new LinkedHashSet<>();
    private final Set<String> hostnames = new LinkedHashSet<>();
    /** Compiled patterns for all sensitivePathPrefixes (literal + glob), sorted longest-first. */
    private final List<Pattern> pathPatterns = new ArrayList<>();
    private final List<String> redactions = new ArrayList<>();

    public RedactionTransformer() {
        this(new RedactionConfig());
    }

    public RedactionTransformer(@NotNull RedactionConfig config) {
        this.config = new RedactionConfig(config);
    }

    /** Convenience: create with defaults. */
    public static RedactionTransformer withDefaults() {
        return new RedactionTransformer();
    }

    /** Descriptions of what was redacted. Available after {@link #transform}. */
    public List<String> redactions() { return Collections.unmodifiableList(redactions); }

    /** Usernames found and redacted. */
    public Set<String> usernames() { return Collections.unmodifiableSet(usernames); }

    /** Primary hostname found and redacted (null if none). */
    public @Nullable String hostname() { return hostnames.isEmpty() ? null : hostnames.iterator().next(); }

    /** All hostnames found and redacted. */
    public Set<String> hostnames() { return Collections.unmodifiableSet(hostnames); }

    /** The config this transformer was created with. */
    public RedactionConfig config() { return config; }

    @Override
    public @NotNull HsErrReport transform(@NotNull HsErrReport report) {
        scan(report);
        return super.transform(report);
    }

    // ── Pass 1: scanning ─────────────────────────────────────────────────

    private void scan(HsErrReport report) {
        // Seed from config
        usernames.addAll(config.additionalUsernames());
        hostnames.addAll(config.additionalHostnames());

        if (report.header() != null) {
            scanText(report.header().jreVersion());
            scanText(report.header().problematicFrame());
            scanText(report.header().coreDumpLine());
            scanText(report.header().errorDetail());
        }
        if (report.summary() != null) {
            if (config.redactHostnames() && report.summary().hostname() != null) {
                hostnames.add(report.summary().hostname());
            }
            scanText(report.summary().commandLine());
        }
        scanItems(report.thread());
        scanItems(report.process());
        scanItems(report.system());

        // Build path patterns: globs use globToPattern, literals use boundary-aware regex
        // Sort by descending source length so longest prefixes match first
        List<String> prefixes = new ArrayList<>(config.sensitivePathPrefixes());
        prefixes.sort(Comparator.comparingInt(String::length).reversed());
        for (String prefix : prefixes) {
            if (isGlob(prefix)) {
                pathPatterns.add(globToPattern(prefix));
            } else {
                String normalized = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
                // Match the literal prefix only at a path boundary: the prefix must be followed
                // by '/' (subpath continues), end-of-string, or a non-path char (space, quote, etc.)
                // — but NOT by letters/digits that would make it a longer component (e.g. /priv vs /private).
                // Then consume the rest of the path segment (non-whitespace).
                pathPatterns.add(Pattern.compile(
                        Pattern.quote(normalized) + "(?=/|$|(?=[^\\w.+-]))\\S*"));
            }
        }
    }

    /** Whether the prefix contains glob wildcards. */
    private static boolean isGlob(String prefix) {
        return prefix.contains("*") || prefix.contains("?");
    }

    /** Convert a glob pattern to a compiled regex that matches path prefixes.
     *  {@code **} matches across separators, {@code *} matches within one segment,
     *  {@code ?} matches a single character. */
    private static Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                sb.append(".*");
                i += 2;
            } else if (c == '*') {
                sb.append("[^/]*");
                i++;
            } else if (c == '?') {
                sb.append(".");
                i++;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        // Match the glob and everything after it (path continuation)
        return Pattern.compile(sb + "\\S*");
    }

    private void scanItems(@Nullable ThreadSection section) {
        if (section == null) return;
        for (SectionItem item : section.items()) scanItem(item);
    }

    private void scanItems(@Nullable ProcessSection section) {
        if (section == null) return;
        for (SectionItem item : section.items()) scanItem(item);
    }

    private void scanItems(@Nullable SystemSection section) {
        if (section == null) return;
        if (section.vmInfo() != null) scanText(section.vmInfo().line());
        for (SectionItem item : section.items()) scanItem(item);
    }

    private void scanItem(SectionItem item) {
        if (item instanceof BlankLine) {
            // blank separators contain nothing to scan
        } else if (item instanceof VmInfo vi) {
            scanText(vi.line());
        } else if (item instanceof FrameList fl) {
            for (StackFrame f : fl.frames()) scanText(f.line());
        } else if (item instanceof ErrorDuringError ede) {
            scanText(ede.line());
        } else if (item instanceof Registers reg) {
            for (String l : reg.registerLines()) scanText(l);
        } else if (item instanceof DynamicLibraries dl) {
            for (DynamicLibraries.LibraryEntry e : dl.entries()) {
                if (e.path() != null) scanText(e.path());
            }
        } else if (item instanceof VmArguments va) {
            for (String l : va.lines()) scanText(l);
        } else if (item instanceof EnvironmentVariables ev) {
            for (var entry : ev.vars().entrySet()) scanText(entry.getValue());
        } else if (item instanceof EventLog el) {
            for (String e : el.events()) scanText(e);
        } else if (item instanceof GlobalFlags gf) {
            for (String l : gf.bodyLines()) scanText(l);
        } else if (item instanceof CompileTasksInfo ct) {
            scanText(ct.name());
            for (String l : ct.lines()) scanText(l);
        } else if (item instanceof ReleaseFileInfo rf) {
            scanText(rf.name());
            for (String l : rf.lines()) scanText(l);
        } else if (item instanceof VmMutexInfo vm) {
            scanText(vm.name());
            scanText(vm.ownerState());
            for (String l : vm.lines()) scanText(l);
        } else if (item instanceof OsInfo oi) {
            scanText(oi.name());
            for (String l : oi.lines()) {
                scanText(l);
                if (config.redactHostnames() && l.startsWith("uname:")) {
                    extractHostnameFromUname(l);
                }
            }
        } else if (item instanceof CpuInfo ci) {
            scanText(ci.name());
            for (String l : ci.lines()) scanText(l);
        } else if (item instanceof HeapRegions hr) {
            scanText(hr.name());
            for (String l : hr.lines()) scanText(l);
        } else if (item instanceof NamedSection ns) {
            scanText(ns.name());
            for (String l : ns.lines()) {
                scanText(l);
            }
        } else if (item instanceof ThreadList tl) {
            for (ThreadEntry te : tl.entries()) scanText(te.line());
        } else if (item instanceof SignalInfo si) {
            scanText(si.toString());
        } else if (item instanceof StackBoundsInfo sbi) {
            scanText(sbi.toString());
        } else if (item instanceof MemoryInfo mi) {
            scanText(mi.toString());
        }
    }

    /** Extract hostname from "uname: Linux HOSTNAME ..." or "uname: Darwin HOSTNAME ..." */
    private void extractHostnameFromUname(String unameLine) {
        // Format: "uname: <OS> [HOSTNAME] <version> ..."
        // Linux: "uname: Linux ls3607 6.4.0-..."
        // Darwin with hostname: "uname: Darwin HGJHGLJ 22.3.0 ..."
        // Darwin without hostname: "uname: Darwin 24.6.0 ..."
        String after = unameLine.substring("uname:".length()).trim();
        String[] parts = after.split("\\s+", 3);
        if (parts.length >= 3) {
            String os = parts[0]; // "Linux" or "Darwin"
            String second = parts[1]; // hostname or version number
            // If second token is NOT a version number (not starting with digit), it's a hostname
            if (!second.isEmpty() && !Character.isDigit(second.charAt(0))) {
                hostnames.add(second);
            }
        }
    }

    private void scanText(@Nullable String text) {
        if (text == null || !config.redactUsernames()) return;
        Matcher m = USERNAME_IN_PATH.matcher(text);
        while (m.find()) {
            usernames.add(m.group(1));
        }
    }

    // ── Pass 2: transforming ─────────────────────────────────────────────

    @Override
    protected @Nullable Header transformHeader(@NotNull Header header) {
        Header.Builder b = header.toBuilder();
        boolean changed = false;

        String detail = header.errorDetail();
        if (config.redactPids() && header.pid() != null) {
            detail = detail.replace("pid=" + header.pid(), "pid=" + config.pidPlaceholder());
            b.pid(config.pidPlaceholder());
            redactions.add("Redacted PID: " + header.pid());
            changed = true;
        }
        if (config.redactPids() && header.tid() != null) {
            detail = detail.replace("tid=" + header.tid(), "tid=" + config.tidPlaceholder());
            b.tid(config.tidPlaceholder());
            redactions.add("Redacted TID: " + header.tid());
            changed = true;
        }
        if (changed) b.errorDetail(detail);

        // Redact text in header fields
        detail = redactText(detail);
        if (!detail.equals(header.errorDetail())) { b.errorDetail(detail); changed = true; }

        String frame = header.problematicFrame();
        if (frame != null) {
            String redacted = redactText(frame);
            if (!redacted.equals(frame)) { b.problematicFrame(redacted); changed = true; }
        }

        String jre = header.jreVersion();
        if (jre != null) {
            String redacted = redactText(jre);
            if (!redacted.equals(jre)) { b.jreVersion(redacted); changed = true; }
        }

        String core = header.coreDumpLine();
        if (core != null) {
            String redacted = redactText(core);
            if (!redacted.equals(core)) { b.coreDumpLine(redacted); changed = true; }
        }

        return changed ? b.build() : header;
    }

    @Override
    protected @Nullable Summary transformSummary(@NotNull Summary summary) {
        boolean changed = false;
        String host = summary.host();
        String cmd = summary.commandLine();

        if (config.redactHostnames() && host != null) {
            String redactedHost = redactHostnames(host);
            if (!redactedHost.equals(host)) {
                host = redactedHost;
                redactions.add("Redacted hostname in summary");
                changed = true;
            }
        }
        if (cmd != null) {
            String redacted = redactText(cmd);
            if (!redacted.equals(cmd)) { cmd = redacted; changed = true; }
        }

        return changed
                ? new Summary(summary.banner(), summary.preLines(), cmd,
                              summary.midLines(), host, summary.time(), summary.postLines())
                : summary;
    }

    @Override
    protected @Nullable SectionItem transformBlankLine(@NotNull BlankLine line) {
        return line; // blank separators have nothing to redact
    }

    @Override
    protected @Nullable SectionItem transformThreadInfo(@NotNull ThreadInfo info) {
        String redacted = redactText(info.line());
        if (config.redactThreadNames() && info.name() != null) {
            redacted = redacted.replace("\"" + info.name() + "\"",
                    "\"" + config.redactionText() + "\"");
        }
        return redacted.equals(info.line()) ? info
                : new ThreadInfo(redacted, info.address(), info.threadType(),
                        config.redactThreadNames() ? config.redactionText() : info.name(),
                        info.state(), info.id());
    }

    @Override
    protected @Nullable SectionItem transformErrorDuringError(@NotNull ErrorDuringError error) {
        String redacted = redactText(error.line());
        if (redacted.equals(error.line())) return error;
        String detail = error.detail() != null ? redactText(error.detail()) : null;
        return new ErrorDuringError(redacted, error.step(), error.id(), detail);
    }

    @Override
    protected @Nullable SectionItem transformRegisters(@NotNull Registers registers) {
        List<String> redacted = registers.registerLines().stream()
                .map(this::redactText)
                .collect(Collectors.toList());
        return redacted.equals(registers.registerLines()) ? registers : new Registers(redacted);
    }

    @Override
    protected @Nullable SectionItem transformEventLog(@NotNull EventLog eventLog) {
        if (config.removeEventLogs()) return null;
        List<String> redacted = eventLog.events().stream()
                .map(this::redactText)
                .collect(Collectors.toList());
        return redacted.equals(eventLog.events()) ? eventLog : new EventLog(eventLog.header(), redacted);
    }

    @Override
    protected @Nullable SectionItem transformNamedSection(@NotNull NamedSection section) {
        String newName = redactText(section.name());
        List<String> newLines = section.lines().stream()
                .map(this::redactText)
                .collect(Collectors.toList());
        boolean changed = !newName.equals(section.name()) || !newLines.equals(section.lines());
        if (!changed) return section;
        return new NamedSection(newName, newLines);
    }

    @Override
    protected @Nullable SectionItem transformHeapRegions(@NotNull HeapRegions info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? HeapRegions.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformOsInfo(@NotNull OsInfo info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? new OsInfo(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformCpuInfo(@NotNull CpuInfo info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? new CpuInfo(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformActiveLocale(@NotNull ActiveLocale info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? ActiveLocale.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformLoggingConfig(@NotNull LoggingConfig info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? LoggingConfig.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformSignalHandlers(@NotNull SignalHandlers info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? SignalHandlers.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformNativeMemoryTracking(@NotNull NativeMemoryTracking info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? NativeMemoryTracking.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformGcPreciousLog(@NotNull GcPreciousLog info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? GcPreciousLog.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformHeapSummary(@NotNull HeapSummary info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? HeapSummary.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformSmrInfo(@NotNull SmrInfo info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? SmrInfo.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformCompileTasksInfo(@NotNull CompileTasksInfo info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? new CompileTasksInfo(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformReleaseFileInfo(@NotNull ReleaseFileInfo info) {
        String newName = redactText(info.name());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newLines.equals(info.lines());
        return changed ? ReleaseFileInfo.fromLines(newName, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformVmMutexInfo(@NotNull VmMutexInfo info) {
        String newName = redactText(info.name());
        String newOwnerState = redactText(info.ownerState());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newName.equals(info.name()) || !newOwnerState.equals(info.ownerState()) || !newLines.equals(info.lines());
        return changed ? VmMutexInfo.fromLines(newName + newOwnerState, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformVmInfo(@NotNull VmInfo info) {
        String redacted = redactText(info.line());
        return redacted.equals(info.line()) ? info : VmInfo.fromLine(redacted);
    }

    @Override
    protected @Nullable SectionItem transformMetaspaceInfo(@NotNull MetaspaceInfo info) {
        String newHeader = redactText(info.header());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newHeader.equals(info.header()) || !newLines.equals(info.lines());
        return changed ? new MetaspaceInfo(newHeader, newLines) : info;
    }

    @Override
    protected @Nullable SectionItem transformCodeCacheInfo(@NotNull CodeCacheInfo info) {
        String newHeader = redactText(info.header());
        List<String> newLines = info.lines().stream().map(this::redactText).collect(Collectors.toList());
        boolean changed = !newHeader.equals(info.header()) || !newLines.equals(info.lines());
        return changed ? new CodeCacheInfo(newHeader, newLines) : info;
    }

    @Override
    protected @Nullable StackFrame transformStackFrame(@NotNull StackFrame frame) {
        String redacted = redactText(frame.line());
        return redacted.equals(frame.line()) ? frame : new StackFrame(redacted, frame.type());
    }

    @Override
    protected @Nullable SectionItem transformDynamicLibraries(@NotNull DynamicLibraries libs) {
        if (config.removeDynamicLibraries()) return null;
        boolean changed = false;
        var redacted = new java.util.ArrayList<DynamicLibraries.LibraryEntry>(libs.entries().size());
        for (var e : libs.entries()) {
            if (e.path() != null) {
                String newPath = redactText(e.path());
                if (!newPath.equals(e.path())) {
                    redacted.add(new DynamicLibraries.LibraryEntry(e.prefix(), newPath));
                    changed = true;
                    continue;
                }
            }
            redacted.add(e);
        }
        return changed ? new DynamicLibraries(libs.header(), redacted) : libs;
    }

    @Override
    protected @Nullable SectionItem transformVmArguments(@NotNull VmArguments args) {
        List<String> redacted = args.lines().stream()
                .map(this::redactText)
                .collect(Collectors.toList());
        return redacted.equals(args.lines()) ? args : VmArguments.fromLines(args.header(), redacted);
    }

    @Override
    protected @Nullable SectionItem transformGlobalFlags(@NotNull GlobalFlags flags) {
        List<String> redacted = flags.bodyLines().stream()
                .map(this::redactText)
                .collect(Collectors.toList());
        return redacted.equals(flags.bodyLines()) ? flags : GlobalFlags.fromLines(flags.header(), redacted);
    }

    @Override
    protected @Nullable SectionItem transformEnvironmentVariables(@NotNull EnvironmentVariables vars) {
        var newVars = new java.util.LinkedHashMap<String, String>();
        boolean changed = false;
        for (var entry : vars.vars().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (config.redactEnvVars() && !config.safeEnvVars().contains(key)) {
                newVars.put(key, config.redactionText());
                redactions.add("Redacted env var: " + key);
                changed = true;
            } else {
                String redacted = redactText(value);
                newVars.put(key, redacted);
                if (!redacted.equals(value)) changed = true;
            }
        }
        return changed ? new EnvironmentVariables(vars.header(), newVars) : vars;
    }

    @Override
    protected @Nullable ThreadEntry transformThreadEntry(@NotNull ThreadEntry entry) {
        String redacted = redactText(entry.line());
        if (config.redactThreadNames() && entry.name() != null) {
            redacted = redacted.replace("\"" + entry.name() + "\"",
                    "\"" + config.redactionText() + "\"");
        }
        return redacted.equals(entry.line()) ? entry
                : new ThreadEntry(redacted, entry.current(), entry.address(),
                        entry.threadType(),
                        config.redactThreadNames() ? config.redactionText() : entry.name(),
                        entry.daemon(), entry.state(), entry.osThreadId(),
                        entry.stackStart(), entry.stackEnd(), entry.stackSize(), entry.smrInfo());
    }

    // SignalInfo, StackBoundsInfo, MemoryInfo: structured fields don't contain PII,
    // so no transform override needed — identity transform suffices.

    // ── text redaction engine ────────────────────────────────────────────

    private String redactHostnames(String text) {
        String result = text;
        for (String host : hostnames) {
            result = result.replace(host, config.hostPlaceholder());
        }
        return result;
    }

    private String redactText(@Nullable String text) {
        if (text == null) return null;
        String result = text;

        // 1. Sensitive path prefix replacement (longest match first, boundary-aware)
        for (Pattern p : pathPatterns) {
            result = p.matcher(result).replaceAll(Matcher.quoteReplacement(config.pathPlaceholder()));
        }

        // 2. Username replacement in paths
        if (config.redactUsernames()) {
            for (String username : usernames) {
                result = result.replace("/" + username + "/", "/" + config.userPlaceholder() + "/");
                result = result.replace("/" + username + "\"", "/" + config.userPlaceholder() + "\"");
                result = result.replace("/" + username + " ", "/" + config.userPlaceholder() + " ");
                result = result.replace("/" + username + ")", "/" + config.userPlaceholder() + ")");
                result = result.replace("/" + username + ":", "/" + config.userPlaceholder() + ":");
                if (result.endsWith("/" + username)) {
                    result = result.substring(0, result.length() - username.length()) + config.userPlaceholder();
                }
            }
        }

        // 3. Hostname replacement (all discovered + configured hostnames)
        if (config.redactHostnames()) {
            result = redactHostnames(result);
        }

        // 4. Sensitive string replacement
        for (String s : config.sensitiveStrings()) {
            if (result.contains(s)) {
                result = result.replace(s, config.redactionText());
            }
        }

        // 5. IP address redaction
        if (config.redactIpAddresses()) {
            result = RedactionConfig.IPV4_PATTERN.matcher(result)
                    .replaceAll(config.redactionText());
        }

        return result;
    }
}
