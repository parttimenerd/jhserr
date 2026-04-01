package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.model.*;
import me.bechberger.jhserr.HsErrReport;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parser for HotSpot error report (hs_err) files.
 *
 * <p>Produces a fully-parsed {@link HsErrReport} with no raw line storage.
 * Each section uses ordered item lists for faithful round-trip reproduction:
 * {@code parse(file).toString()} produces the exact original content.
 */
public class HsErrParser {

    private static final String SUMMARY_BANNER = "---------------  S U M M A R Y";
    private static final String THREAD_BANNER  = "---------------  T H R E A D";
    private static final String PROCESS_BANNER = "---------------  P R O C E S S";
    private static final String SYSTEM_BANNER  = "---------------  S Y S T E M";

    private final List<String> lines;
    private int pos;

    private HsErrParser(List<String> lines) {
        this.lines = lines;
        this.pos = 0;
    }

    public static HsErrReport parse(Path file) throws IOException {
        return parse(Files.readString(file));
    }

    public static HsErrReport parse(String content) {
        return new HsErrParser(Arrays.asList(content.replace("\r\n", "\n").split("\n", -1))).doParse();
    }

    public static HsErrReport parse(List<String> lines) {
        return new HsErrParser(new ArrayList<>(lines)).doParse();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String current() { return pos < lines.size() ? lines.get(pos) : null; }
    private String advance() { String l = current(); pos++; return l; }
    private boolean atEnd() { return pos >= lines.size(); }

    private boolean startsWith(String prefix) {
        return !atEnd() && current().startsWith(prefix);
    }

    private boolean isSectionBanner() {
        if (atEnd()) return false;
        String l = current();
        return l.startsWith(SUMMARY_BANNER) || l.startsWith(THREAD_BANNER)
            || l.startsWith(PROCESS_BANNER) || l.startsWith(SYSTEM_BANNER);
    }

    private int consumeBlanks() {
        int count = 0;
        while (!atEnd() && current().isEmpty()) { advance(); count++; }
        return count;
    }

    // ── main orchestration ───────────────────────────────────────────────

    private HsErrReport doParse() {
        Header header = parseHeader();
        Summary summary = startsWith(SUMMARY_BANNER) ? parseSummary() : null;
        ThreadSection thread = startsWith(THREAD_BANNER) ? parseThreadSection() : null;
        ProcessSection process = startsWith(PROCESS_BANNER) ? parseProcessSection() : null;
        SystemSection system = startsWith(SYSTEM_BANNER) ? parseSystemSection() : null;
        String endMarker = collectEndMarker();
        return new HsErrReport(header, summary, thread, process, system, endMarker);
    }

    private String collectEndMarker() {
        List<String> remaining = new ArrayList<>();
        while (pos < lines.size()) remaining.add(lines.get(pos++));
        return String.join("\n", remaining);
    }

    // ── HEADER ───────────────────────────────────────────────────────────

    private static final Pattern PID_PAT = Pattern.compile("pid=(\\d+)");
    private static final Pattern TID_PAT = Pattern.compile("tid=(\\d+)");

    private Header parseHeader() {
        Header.Builder b = Header.builder();
        boolean afterUrl = false;
        List<String> extraDetailLines = new ArrayList<>();

        while (!atEnd() && current().startsWith("#")) {
            String raw = advance();
            // Strip "# " or "#" prefix to get content
            String content;
            if (raw.startsWith("# ")) content = raw.substring(2);
            else if (raw.length() > 1) content = raw.substring(1);
            else content = "";
            String stripped = content.strip();

            if (stripped.isEmpty()) continue;
            if (stripped.equals("A fatal error has been detected by the Java Runtime Environment:")) continue;

            if (stripped.startsWith("SIGSEGV") || stripped.startsWith("SIGBUS")
                || stripped.startsWith("SIGFPE") || stripped.startsWith("SIGILL")
                || stripped.startsWith("SIGTRAP")
                || stripped.startsWith("Internal Error") || stripped.startsWith("Out of Memory")) {
                b.errorDetail(stripped);
                String type = stripped.contains("(")
                    ? stripped.substring(0, stripped.indexOf('(')).strip()
                    : stripped.split("\\s")[0];
                b.errorType(type);
                Matcher m = PID_PAT.matcher(stripped);
                if (m.find()) b.pid(m.group(1));
                m = TID_PAT.matcher(stripped);
                if (m.find()) b.tid(m.group(1));
                // Check next line for assert detail
                if (!atEnd() && current().startsWith("#")) {
                    String nextStripped = current().replaceFirst("^#\\s?", "").strip();
                    if (nextStripped.startsWith("assert") || nextStripped.startsWith("guarantee")
                        || nextStripped.contains("failed:")
                        || nextStripped.startsWith("fatal error:")
                        || nextStripped.startsWith("Error:")) {
                        b.assertDetail(nextStripped);
                        advance();
                    }
                }
                // Check for non-# diagnostic detail lines (e.g. Shenandoah assert details)
                if (!atEnd() && !current().startsWith("#") && !isSectionBanner()) {
                    while (!atEnd() && !current().startsWith("#") && !isSectionBanner()) {
                        extraDetailLines.add(advance());
                    }
                    // Remove trailing blank lines from extraDetailLines (they separate from next # block)
                    while (!extraDetailLines.isEmpty() && extraDetailLines.get(extraDetailLines.size() - 1).isEmpty()) {
                        extraDetailLines.remove(extraDetailLines.size() - 1);
                    }
                }
                continue;
            }
            if (stripped.startsWith("JRE version:")) {
                b.jreVersion(stripped.substring("JRE version:".length()));
                continue;
            }
            if (stripped.startsWith("Java VM:")) {
                b.javaVm(stripped.substring("Java VM:".length()));
                continue;
            }
            if (stripped.equals("Problematic frame:")) {
                if (!atEnd() && current().startsWith("#")) {
                    String fRaw = advance();
                    b.problematicFrame(fRaw.startsWith("# ") ? fRaw.substring(2) : fRaw.substring(1));
                }
                continue;
            }
            if (stripped.startsWith("No core dump") || stripped.startsWith("Core dump will be")
                || stripped.startsWith("Core dump is writing")
                || stripped.startsWith("CreateCoredumpOnCrash")) {
                b.coreDumpLine(stripped);
                continue;
            }
            if (stripped.startsWith("No JFR") || stripped.startsWith("JFR recording")) {
                b.jfrFileLine(stripped);
                continue;
            }
            if (stripped.contains("submit a bug report")) {
                if (!atEnd() && current().startsWith("#")) {
                    String urlLine = advance();
                    b.bugReportUrl(urlLine.replaceFirst("^#\\s*", "").strip());
                }
                afterUrl = true;
                continue;
            }
            if (afterUrl && stripped.contains("crash happened outside the Java Virtual Machine")) {
                b.crashedOutsideJvm(true);
                // Consume "See problematic frame..." line
                if (!atEnd() && current().startsWith("#")) {
                    String next = current().replaceFirst("^#\\s?", "").strip();
                    if (next.startsWith("See problematic frame")) advance();
                }
            }
        }

        b.extraDetailLines(extraDetailLines);
        b.trailingBlankLines(consumeBlanks());
        return b.build();
    }

    // ── SUMMARY ──────────────────────────────────────────────────────────

    private Summary parseSummary() {
        String banner = advance();
        List<String> preLines = new ArrayList<>();
        String commandLine = null;
        List<String> midLines = new ArrayList<>();
        String host = null;
        String time = null;
        List<String> postLines = new ArrayList<>();

        int phase = 0; // 0=pre, 1=mid, 2=post
        while (!atEnd() && !isSectionBanner()) {
            String l = current();
            if (l.startsWith("Command Line: ")) {
                commandLine = advance().substring("Command Line: ".length());
                phase = 1;
                continue;
            }
            if (l.startsWith("Host: ")) {
                host = advance().substring("Host: ".length());
                phase = 2;
                continue;
            }
            if (l.startsWith("Time: ")) {
                time = advance().substring("Time: ".length());
                phase = 2;
                continue;
            }
            switch (phase) {
                case 0 -> preLines.add(advance());
                case 1 -> midLines.add(advance());
                case 2 -> postLines.add(advance());
            }
        }

        return new Summary(banner, preLines, commandLine, midLines, host, time, postLines);
    }

    // ── THREAD SECTION ───────────────────────────────────────────────────

    private static final Pattern CURRENT_THREAD_PAT = Pattern.compile(
        "Current thread \\((0x[0-9a-fA-F]+)\\):\\s+(\\S+)\\s+\"(.+?)\"\\s+\\[(.+?)(?:, id=(\\d+))?.*\\]");

    private ThreadSection parseThreadSection() {
        String banner = advance();
        List<ThreadSectionItem> items = new ArrayList<>();

        while (!atEnd() && !isSectionBanner() && !"END.".equals(current())) {
            String l = current();

            if (l.isEmpty()) { items.add(new BlankLine()); advance(); continue; }

            if (l.startsWith("Current thread")) {
                items.add(parseCurrentThread(advance()));
                continue;
            }
            if (l.startsWith("Native frames:") || l.startsWith("Java frames:")) {
                items.add(parseFrameList());
                continue;
            }
            if (l.startsWith("Registers:")) {
                items.add(parseRegisters());
                continue;
            }
            if (l.startsWith("[error occurred during error reporting")) {
                items.add(parseErrorDuringError(advance()));
                continue;
            }
            // Known multi-line sub-sections
            if (isThreadGenericHeader(l)) {
                items.add(parseGenericBlockUntil(this::isThreadItemStarter));
                continue;
            }
            // siginfo: parsed as SignalInfo if possible
            if (l.startsWith("siginfo:")) {
                String sigLine = advance();
                SignalInfo si = SignalInfo.parse(sigLine);
                items.add(si != null ? si : new NamedSection(sigLine, List.of()));
                continue;
            }
            // Stack bounds: parsed as StackBoundsInfo if possible
            if (l.startsWith("Stack: [")) {
                String stackLine = advance();
                StackBoundsInfo sbi = StackBoundsInfo.parse(stackLine);
                items.add(sbi != null ? sbi : new NamedSection(stackLine, List.of()));
                continue;
            }
            // Other single-line sub-sections
            // Everything else: standalone line
            items.add(new NamedSection(advance(), List.of()));
        }

        return new ThreadSection(banner, items);
    }

    private boolean isThreadGenericHeader(String l) {
        return l.startsWith("Top of Stack:") || l.startsWith("Instructions ")
            || l.startsWith("Register to memory mapping:")
            || l.startsWith("Stack slot to memory mapping:")
            || l.startsWith("Lock stack of current Java thread")
            || l.startsWith("JavaThread ");
    }

    private boolean isThreadItemStarter(String l) {
        return l.startsWith("Current thread") || l.startsWith("Native frames:")
            || l.startsWith("Java frames:") || l.startsWith("Registers:")
            || l.startsWith("[error occurred") || l.startsWith("Retrying")
            || l.startsWith("siginfo:") || l.startsWith("Stack: [")
            || l.startsWith("Current CompileTask:")
            || l.startsWith("JavaThread ")
            || l.startsWith("Lock stack of current Java thread")
            || isThreadGenericHeader(l);
    }

    private ThreadInfo parseCurrentThread(String line) {
        Matcher m = CURRENT_THREAD_PAT.matcher(line);
        if (m.find()) {
            return new ThreadInfo(line, m.group(1), m.group(2), m.group(3),
                                  m.group(4), m.group(5));
        }
        return new ThreadInfo(line, null, null, line, null, null);
    }

    private FrameList parseFrameList() {
        String header = advance();
        List<StackFrame> frames = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            if (l.startsWith("[error")) break;
            if (l.startsWith("Registers:")) break;
            if (l.startsWith("siginfo:")) break;
            if (l.startsWith("Native frames:") || l.startsWith("Java frames:")) break;
            if (l.startsWith("Retrying")) break;
            if (isSectionBanner()) break;
            frames.add(parseStackFrame(advance()));
        }
        return new FrameList(header, frames);
    }

    private StackFrame parseStackFrame(String line) {
        StackFrame.Type type = StackFrame.Type.OTHER;
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        if (i < line.length()) {
            type = StackFrame.Type.fromCode(line.charAt(i));
        }
        return new StackFrame(line, type);
    }

    private Registers parseRegisters() {
        advance(); // skip "Registers:" header
        List<String> registerLines = new ArrayList<>();
        while (!atEnd() && !current().isEmpty()) {
            String l = current();
            if (isThreadItemStarter(l) || isSectionBanner()) break;
            registerLines.add(advance());
        }
        return new Registers(registerLines);
    }

    private static final Pattern ERROR_PAT = Pattern.compile(
        "\\[error occurred during error reporting \\((.+?)\\),\\s*id (0x[0-9a-fA-F]+),?\\s*(.*)\\]");

    private ErrorDuringError parseErrorDuringError(String line) {
        Matcher m = ERROR_PAT.matcher(line);
        if (m.find()) {
            return new ErrorDuringError(line, m.group(1), m.group(2), m.group(3));
        }
        return new ErrorDuringError(line, null, null, null);
    }

    // ── PROCESS SECTION ──────────────────────────────────────────────────

    private static final Pattern EVENT_LOG_PAT = Pattern.compile(".*\\(\\d+ events?\\):.*");
    private static final Pattern THREAD_ENTRY_PAT = Pattern.compile(
        "^(=>)?\\s*(0x[0-9a-fA-F]+)\\s+(\\w+)\\s+\"(.+?)\"\\s*(daemon)?.*");

    // New format: [STATE, id=N, stack(START,END) (SIZEK)] or [id=N, stack(START,END) (SIZEK)]
    private static final Pattern NEW_BRACKET_PAT = Pattern.compile(
        "\\[(?:(?<state>_\\w+),\\s*)?id=(?<id>\\d+),\\s*stack\\((?<start>0x[0-9a-fA-F]+),(?<end>0x[0-9a-fA-F]+)\\)(?:\\s+\\((?<size>\\d+K)\\))?\\]");

    // Old format: [stack: START,END]
    private static final Pattern OLD_STACK_PAT = Pattern.compile(
        "\\[stack:\\s*(?<start>0x[0-9a-fA-F]+),(?<end>0x[0-9a-fA-F]+)\\]");

    // Old format: [id=N] (separate bracket)
    private static final Pattern OLD_ID_PAT = Pattern.compile("\\[id=(?<id>\\d+)\\]");

    // SMR info suffix after last ]
    private static final Pattern SMR_PAT = Pattern.compile("\\]\\s+(_threads_hazard_ptr=.+)$");

    private ProcessSection parseProcessSection() {
        String banner = advance();
        List<ProcessSectionItem> items = new ArrayList<>();

        while (!atEnd() && !isSectionBanner() && !"END.".equals(current())) {
            String l = current();

            if (l.isEmpty()) { items.add(new BlankLine()); advance(); continue; }

            // Thread lists
            if (l.startsWith("Java Threads:") || l.startsWith("Other Threads:")) {
                items.add(parseThreadList());
                continue;
            }
            // Event logs: "Name (N events):" pattern
            if (EVENT_LOG_PAT.matcher(l).matches()) {
                items.add(parseEventLog());
                continue;
            }
            // Dynamic libraries
            if (l.startsWith("Dynamic libraries:")) {
                items.add(parseDynamicLibraries());
                continue;
            }
            // VM Arguments
            if (l.startsWith("VM Arguments:")) {
                items.add(parseVmArguments());
                continue;
            }
            // Environment Variables
            if (l.startsWith("Environment Variables:")) {
                items.add(parseEnvironmentVariables());
                continue;
            }
            // Known multi-line generic sections
            if (isProcessGenericHeader(l)) {
                items.add(parseProcessNamedBlock());
                continue;
            }
            // [Global flags] + flag entries
            if (l.equals("[Global flags]")) {
                items.add(parseGlobalFlags());
                continue;
            }
            // Typed standalone lines: try parsing into specific SectionItem types
            if (l.startsWith("uid ")) {
                var parsed = UidInfo.parse(l);
                items.add(parsed != null ? parsed : new NamedSection(advance(), List.of()));
                if (parsed != null) advance();
                continue;
            }
            if (l.startsWith("umask:")) {
                var parsed = UmaskInfo.parse(l);
                items.add(parsed != null ? parsed : new NamedSection(advance(), List.of()));
                if (parsed != null) advance();
                continue;
            }
            if (l.startsWith("VM state:")) {
                var parsed = VmStateInfo.parse(l);
                items.add(parsed != null ? parsed : new NamedSection(advance(), List.of()));
                if (parsed != null) advance();
                continue;
            }
            if (l.startsWith("Heap address:")) {
                var parsed = HeapAddressInfo.parse(l);
                items.add(parsed != null ? parsed : new NamedSection(advance(), List.of()));
                if (parsed != null) advance();
                continue;
            }
            if (l.startsWith("Compilation:")) {
                var parsed = CompilationInfo.parse(l);
                items.add(parsed != null ? parsed : new NamedSection(advance(), List.of()));
                if (parsed != null) advance();
                continue;
            }
            // Known standalone process lines → NamedSection with no body
            if (isProcessStandaloneLine(l)) {
                items.add(parseProcessStandaloneLine(advance()));
                continue;
            }
            // Everything else: standalone line
            items.add(new NamedSection(advance(), List.of()));
        }

        return new ProcessSection(banner, items);
    }

    private boolean isProcessGenericHeader(String l) {
        return l.startsWith("Threads class SMR") || l.startsWith("Threads with active compile")
            || l.startsWith("GC Precious Log:") || l.startsWith("Heap:")
            || l.startsWith("Heap Regions:") || l.startsWith("Metaspace")
            || l.startsWith("CodeHeap") // Keep CodeHeap for specialized parsing
            // Note: Skip "CodeCache:" for lenient parsing - handle as generic NamedSection 
            || l.startsWith("Logging:") || l.startsWith("Active Locale:")
            || l.startsWith("Signal Handlers:") || l.startsWith("Native Memory Tracking:")
            || l.startsWith("Preinit state:") || l.startsWith("VM Mutex")
            || l.startsWith("OutOfMemory and StackOverflow")
            || l.startsWith("Internal statistics:")
            || l.startsWith("Release file:")
            || l.startsWith("Decoder state:");
    }

    /** Lines that are single, standalone sub-section items (no multi-line body). */
    private boolean isProcessStandaloneLine(String l) {
        return l.startsWith("CDS archive") || l.startsWith("Compressed class space")
            || l.startsWith("Narrow klass") || l.startsWith("Card table")
            || l.startsWith("Marking Bits") || l.startsWith(" Bits:")
            || l.startsWith("Polling page:") || l.startsWith("Polling Pages")
            || l.startsWith("JVMTI")
            || l.startsWith("UseCompressedClassPointers")
            || l.startsWith("Encoding Range:") || l.startsWith("Klass Range:")
            || l.startsWith("Klass ID Range:") || l.startsWith("Protection zone:")
            || l.startsWith("Unsupported internal testing");
    }

    private ProcessSectionItem parseProcessStandaloneLine(String line) {
        if (line.startsWith("CDS archive")) return CdsInfo.fromLine(line);
        if (line.startsWith("Compressed class space")) return CompressedClassSpaceInfo.fromLine(line);
        if (line.startsWith("Narrow klass")) return NarrowKlassInfo.fromLine(line);
        if (line.startsWith("Polling page:")) return PollingPageInfo.fromLine(line);
        return new NamedSection(line, List.of());
    }

    private GlobalFlags parseGlobalFlags() {
        String header = advance(); // "[Global flags]"
        List<String> body = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            // Flag lines start with whitespace and have {type} {source} pattern
            if (l.startsWith(" ") && l.contains("{")) {
                body.add(advance());
                continue;
            }
            // ccstrlist lines that wrap from previous flag entry
            if (l.startsWith("ccstrlist ") || l.startsWith("    ccstrlist ")) {
                body.add(advance());
                continue;
            }
            break;
        }
        return GlobalFlags.fromLines(header, body);
    }

    private boolean isProcessItemStarter(String l) {
        return l.startsWith("Java Threads:") || l.startsWith("Other Threads:")
            || (EVENT_LOG_PAT.matcher(l).matches() && !l.startsWith("Compilation events"))
            || l.startsWith("Dynamic libraries:") || l.startsWith("VM Arguments:")
            || l.startsWith("Environment Variables:")
            || isProcessGenericHeader(l)
            || isProcessStandaloneLine(l)
            || isProcessTypedLine(l)
            || l.equals("[Global flags]");
    }

    private ProcessSectionItem parseProcessNamedBlock() {
        NamedSection ns = parseGenericBlockUntil(this::isProcessItemStarter);
        return toProcessNamedSection(ns);
    }

    private ProcessSectionItem toProcessNamedSection(NamedSection ns) {
        String name = ns.name();
        List<String> lines = ns.lines();

        if (name.startsWith("Threads class SMR")) return SmrInfo.fromLines(name, lines);
        if (name.startsWith("Threads with active compile")) return new CompileTasksInfo(name, lines);
        if (name.startsWith("GC Precious Log:")) return GcPreciousLog.fromLines(name, lines);
        if (name.startsWith("Heap:")) return HeapSummary.fromLines(name, lines);
        if (name.startsWith("Heap Regions:")) return HeapRegions.fromLines(name, lines);
        if (name.startsWith("Metaspace")) return new MetaspaceInfo(name, lines);
        if (name.startsWith("CodeHeap") || name.startsWith("CodeCache:")) return new CodeCacheInfo(name, lines);
        if (name.startsWith("Logging:")) return LoggingConfig.fromLines(name, lines);
        if (name.startsWith("Active Locale:")) return ActiveLocale.fromLines(name, lines);
        if (name.startsWith("Signal Handlers:")) return SignalHandlers.fromLines(name, lines);
        if (name.startsWith("Native Memory Tracking:")) return NativeMemoryTracking.fromLines(name, lines);
        if (name.startsWith("Release file:")) return ReleaseFileInfo.fromLines(name, lines);
        if (name.startsWith("VM Mutex/Monitor")) return VmMutexInfo.fromLines(name, lines);

        return ns;
    }

    /** Lines that are parsed into typed SectionItem records. */
    private boolean isProcessTypedLine(String l) {
        return l.startsWith("uid ") || l.startsWith("umask:")
            || l.startsWith("VM state:") || l.startsWith("Heap address:")
            || l.startsWith("Compilation:");
    }

    private ThreadList parseThreadList() {
        String header = advance();
        List<ThreadEntry> entries = new ArrayList<>();
        String totalLine = null;
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            if (l.startsWith("Total:")) { totalLine = advance(); continue; }
            if (l.startsWith("=>") || l.matches("^\\s+0x.*")) {
                entries.add(parseThreadEntry(advance()));
            } else {
                break;
            }
        }
        return new ThreadList(header, entries, totalLine);
    }

    private ThreadEntry parseThreadEntry(String line) {
        Matcher m = THREAD_ENTRY_PAT.matcher(line);
        if (!m.find()) {
            return new ThreadEntry(line, false, null, null, line, false);
        }

        boolean current = m.group(1) != null;
        String address = m.group(2);
        String threadType = m.group(3);
        String name = m.group(4);
        boolean daemon = m.group(5) != null;

        // Parse state, id, stack info from bracket contents
        String state = null;
        String osThreadId = null;
        String stackStart = null;
        String stackEnd = null;
        String stackSize = null;
        String smrInfo = null;

        // Try new format: [STATE, id=N, stack(START,END) (SIZEK)]
        Matcher bm = NEW_BRACKET_PAT.matcher(line);
        if (bm.find()) {
            state = bm.group("state");
            osThreadId = bm.group("id");
            stackStart = bm.group("start");
            stackEnd = bm.group("end");
            stackSize = bm.group("size");
        } else {
            // Try old format: [stack: START,END] [id=N]
            Matcher sm = OLD_STACK_PAT.matcher(line);
            if (sm.find()) {
                stackStart = sm.group("start");
                stackEnd = sm.group("end");
            }
            Matcher im = OLD_ID_PAT.matcher(line);
            if (im.find()) {
                osThreadId = im.group("id");
            }
        }

        // SMR info: text after last ] starting with _threads_hazard_ptr
        Matcher smrM = SMR_PAT.matcher(line);
        if (smrM.find()) {
            smrInfo = smrM.group(1).strip();
        }

        return new ThreadEntry(line, current, address, threadType, name, daemon,
                               state, osThreadId, stackStart, stackEnd, stackSize, smrInfo);
    }

    private EventLog parseEventLog() {
        String header = advance();
        List<String> events = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) {
                // Look ahead past blank lines: if next non-blank is still part of this
                // event log (starts with "Event:", "}", or is indented body), keep going
                int saved = pos;
                while (pos < lines.size() && lines.get(pos).isEmpty()) pos++;
                if (!atEnd() && isEventLogContinuation(current())) {
                    // Blank lines are part of this event log; add them
                    while (saved < pos) { events.add(lines.get(saved)); saved++; }
                    continue;
                }
                pos = saved; // restore — blank ends this event log
                break;
            }
            if (isProcessItemStarter(l) || isSectionBanner()) break;
            events.add(advance());
        }
        return new EventLog(header, events);
    }

    /** Returns true if this line is a continuation of an event log body. */
    private boolean isEventLogContinuation(String l) {
        return l.startsWith("Event:") || l.equals("}");
    }

    private DynamicLibraries parseDynamicLibraries() {
        String header = advance();
        List<String> lines = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            if (isSectionBanner()) break;
            lines.add(advance());
        }
        return DynamicLibraries.fromLines(header, lines);
    }

    private VmArguments parseVmArguments() {
        String header = advance();
        List<String> bodyLines = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            if (isProcessItemStarter(l) || isSectionBanner()) break;
            bodyLines.add(advance());
        }
        return VmArguments.fromLines(header, bodyLines);
    }

    private EnvironmentVariables parseEnvironmentVariables() {
        String header = advance();
        List<String> bodyLines = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            if (isProcessItemStarter(l) || isSectionBanner()) break;
            bodyLines.add(advance());
        }
        return EnvironmentVariables.fromLines(header, bodyLines);
    }

    // ── SYSTEM SECTION ───────────────────────────────────────────────────

    private static boolean isSystemSectionHeader(String l) {
        return l.startsWith("OS:") || l.startsWith("CPU:") || l.startsWith("Memory:")
            || l.startsWith("vm_info:") || "END.".equals(l);
    }

    private SystemSection parseSystemSection() {
        String banner = advance();
        List<SystemSectionItem> items = new ArrayList<>();

        while (!atEnd() && !"END.".equals(current())) {
            String l = current();

            if (l.isEmpty()) { items.add(new BlankLine()); advance(); continue; }

            // OS: block → OsInfo
            if (l.startsWith("OS:")) {
                items.add(parseOsInfo());
                continue;
            }
            // CPU: block → CpuInfo
            if (l.startsWith("CPU:")) {
                items.add(parseCpuInfo());
                continue;
            }
            // Memory: line (may be followed by Page Sizes: line)
            if (l.startsWith("Memory:")) {
                items.add(parseMemoryInfoBlock());
                continue;
            }
            // vm_info: line → VmInfo
            if (l.startsWith("vm_info:")) {
                items.add(VmInfo.fromLine(advance()));
                continue;
            }
            // Everything else: standalone line
            items.add(new NamedSection(advance(), List.of()));
        }

        return new SystemSection(banner, items);
    }

    private OsInfo parseOsInfo() {
        String name = advance();
        List<String> body = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            // Stop at known system section headers
            if (l.startsWith("CPU:") || l.startsWith("Memory:")
                || l.startsWith("vm_info:") || "END.".equals(l)) break;
            if (l.isEmpty()) {
                // Peek past blanks: stop if next non-blank is a system header
                int saved = pos;
                while (pos < lines.size() && lines.get(pos).isEmpty()) pos++;
                if (atEnd() || isSystemSectionHeader(current())) {
                    pos = saved;
                    break;
                }
                // Blank lines are part of OS info body
                while (saved < pos) { body.add(lines.get(saved)); saved++; }
                continue;
            }
            body.add(advance());
        }
        return new OsInfo(name, body);
    }

    private CpuInfo parseCpuInfo() {
        String name = advance();
        List<String> body = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            // Stop at known system section headers
            if (l.startsWith("Memory:") || l.startsWith("vm_info:") || "END.".equals(l)) break;
            if (l.isEmpty()) {
                // Peek past blanks: stop if next non-blank is a system header
                int saved = pos;
                while (pos < lines.size() && lines.get(pos).isEmpty()) pos++;
                if (atEnd() || isSystemSectionHeader(current())) {
                    pos = saved;
                    break;
                }
                // Blank lines are part of CPU info body
                while (saved < pos) { body.add(lines.get(saved)); saved++; }
                continue;
            }
            body.add(advance());
        }
        return new CpuInfo(name, body);
    }

    /** Parse Memory: line, optionally followed by Page Sizes: line */
    private SystemSectionItem parseMemoryInfoBlock() {
        String name = advance();
        List<String> body = new ArrayList<>();
        // Memory: is usually a single line, but may be followed by "Page Sizes:"
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) break;
            if (l.startsWith("Page Sizes:") || l.startsWith("Page sizes:")) {
                body.add(advance());
                continue;
            }
            break;
        }
        // Try structured MemoryInfo parse (only for single-line Memory: with no extra body)
        if (body.isEmpty()) {
            MemoryInfo mi = MemoryInfo.parse(name);
            if (mi != null) return mi;
        }
        return new NamedSection(name, body);
    }

    // ── SHARED: named block parser ─────────────────────────────────────

    /**
     * Reads a generic section: header line + body lines.
     * Body ends at blank-line-followed-by-known-header, section banner, or END.
     * If blank line is followed by unknown content, the blanks are included in body.
     */
    private NamedSection parseGenericBlockUntil(java.util.function.Predicate<String> isKnownStarter) {
        String name = advance();
        List<String> body = new ArrayList<>();
        while (!atEnd()) {
            String l = current();
            if (l.isEmpty()) {
                // Peek ahead: if next non-blank is a known starter, stop
                int saved = pos;
                while (pos < lines.size() && lines.get(pos).isEmpty()) pos++;
                if (atEnd() || isKnownStarter.test(current()) || isSectionBanner()
                    || "END.".equals(current())) {
                    pos = saved; // don't consume blanks — they go to parent
                    break;
                }
                // Blanks are part of this section's body
                while (saved < pos) { body.add(lines.get(saved)); saved++; }
                continue;
            }
            if (isKnownStarter.test(l) || isSectionBanner() || "END.".equals(l)) break;
            body.add(advance());
        }
        return new NamedSection(name, body);
    }
}