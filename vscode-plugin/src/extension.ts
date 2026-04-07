import * as vscode from "vscode";

// ---------------------------------------------------------------------------
// Regex patterns
// ---------------------------------------------------------------------------

/** Major section banners:  ---------------  T H R E A D  --------------- */
const BANNER_RE = /^-{10,}\s+(.+?)\s+-{10,}$/;

/** Sub-section headings that stand alone on a line (e.g. "Registers:") */
const SUBSECTION_RE = /^([A-Z][\w /().]+:)\s*$/;

/** Sub-section headings with inline content (e.g. "CPU: total 12 ...") */
const SUBSECTION_INLINE_RE = /^([A-Z][\w /().]+:)\s+\S/;

/**
 * Internal sub-headings that appear inside larger blocks (e.g. inside
 * Metaspace:, CodeCache:, etc.) and should NOT become outline entries.
 */
const SUPPRESSED_HEADINGS = new Set([
  "Usage:",
  "Virtual space:",
  "Chunk freelists:",
  "Non-Class:",
  "Class:",
  "Both:",
  "Event:",
  // Metaspace internals
  "MaxMetaspaceSize:",
  "CompressedClassSpaceSize:",
  "CDS:",
  // NMT internals
  "Total:",
  "MallocLimit:",
  // Logging internals
  "Log output configuration:",
]);

/** Headings that always carry inline content — match even if the general
 *  SUBSECTION_INLINE_RE wouldn't fire (e.g. lower-case start for vm_info). */
const INLINE_HEADINGS = new Set([
  "Stack:",
  "Memory:",
  "CPU:",
  "vm_info:",
  "Card table byte_map:",
  "Polling page:",
  "Host:",
  "Time:",
  "Command Line:",
  "Heap address:",
  "VM state:",
  "Page Sizes:",
  "Open File Descriptors:",
]);

/**
 * System-section headings that start lower-case or have special formatting.
 * These don't match the generic SUBSECTION_RE (requires uppercase start).
 */
const SYSTEM_HEADINGS_LC = new Set([
  "uname:",
  "libc:",
  "vm_info:",
]);

/** Event log headings like  Events (1000 events):  or  Compilation events (250 events): */
const EVENT_LOG_RE = /^([\w /]+ events?\s*\(\d+ events?\):)\s*$/i;

/** Current thread line:  Current thread (0x...):  JavaThread "main" [...] */
const CURRENT_THREAD_RE =
  /^Current thread \(0x[0-9a-f]+\):\s+\w+\s+"([^"]+)"/;

/** Current CompileTask: */
const COMPILE_TASK_RE = /^Current CompileTask:/;

/** siginfo: si_signo: ... */
const SIGINFO_RE = /^siginfo:\s+/;

/** VM_Operation (0x...): ... */
const VM_OPERATION_RE = /^VM_Operation \(/;

/** System section: rlimit line */
const RLIMIT_RE = /^rlimit \(soft\/hard\):/;

/** System section: load average line */
const LOAD_AVG_RE = /^load average:/;

/** System section: OS uptime line */
const OS_UPTIME_RE = /^OS uptime:/;

/** System section: /proc/... or /sys/... or /etc/... file headings (Linux) */
const PROC_PATH_RE = /^(\/(?:proc|sys|etc)\/[\w./()-]+)/;

/** System section: container (cgroup) information: */
const CONTAINER_RE = /^container \(cgroup\) information:/;

/** System section: Process Memory: */
const PROCESS_MEMORY_RE = /^Process Memory:/;

/** System section: NUMA node N: ... */
const NUMA_NODE_RE = /^(NUMA node \d+):?/;

/** System section: Steal ticks ... */
const STEAL_TICKS_RE = /^Steal ticks/;

/** System section: CPU Model and flags from /proc/cpuinfo: */
const CPU_MODEL_RE = /^CPU Model and flags from/;

/** GC Heap History entries: {Heap before/after GC invocations=N (full F): */
const GC_INVOCATION_RE = /\{(?:Heap|heap|metaspace)\s+(before|after)\s+GC\s+invocations=(\d+)\s+\(full\s+(\d+)\)/i;

/** NUMA node information: (parent heading, keep; children are noise) */
const NUMA_INFO_RE = /^NUMA node information:/;

/** Horizontal rule (code blob separator): 80+ dashes */
const HLINE_RE = /^-{80,}$/;

/**
 * Header error-type extraction:
 *   #  SIGSEGV (0xb) at pc=...
 *   #  Internal Error (file:line), ...
 *   #  Out of Memory Error (file:line), ...
 */
const HEADER_ERROR_RE =
  /^#\s+(SIGSEGV|SIGBUS|SIGFPE|SIGILL|SIGTRAP|EXCEPTION_\w+|Internal Error|Out of Memory Error)\b/;

/** Problematic frame (V/C/J/j/v frame line in header) — capture function name */
const HEADER_FRAME_RE = /^#\s+[VvCJjA]\s+(?:\[\S+\]\s+)?(.+?)(?:\+0x[0-9a-fA-F]+)?\s*$/;

// ---------------------------------------------------------------------------
// Content detection
// ---------------------------------------------------------------------------

/** The bug-report URL that appears in every hs_err header. */
const BUG_REPORT_URL = "https://bugreport.java.com/bugreport/crash.jsp";

/**
 * Heuristic content-based detection for hs_err / VM.info files.
 * Requires BOTH:
 *  1. A characteristic marker in the # header (bug-report URL, or
 *     JRE version / Java VM lines from jcmd VM.info output), AND
 *  2. A spaced-letter section banner (e.g. ---  T H R E A D  ---).
 */
function looksLikeHsErr(document: vscode.TextDocument): boolean {
  const lang = document.languageId;
  // Skip if already detected, or if the language is a rich/programming language
  if (lang === "hserr" || /^(?:java|python|c|cpp|csharp|javascript|typescript|json|xml|html|css|markdown|yaml|toml|ini|sql|ruby|go|rust|swift|kotlin|scala|php|perl|bash|zsh|sh|powershell|bat|dockerfile|makefile|cmake|properties)$/.test(lang)) {
    return false;
  }

  const scanLines = Math.min(document.lineCount, 150);
  let hasMarker = false;
  let hasSectionBanner = false;

  for (let i = 0; i < scanLines; i++) {
    const text = document.lineAt(i).text;

    if (text.includes(BUG_REPORT_URL)) {
      hasMarker = true;
    }
    if (/^#\s+(?:JRE version:|Java VM:)/.test(text)) {
      hasMarker = true;
    }

    if (BANNER_RE.test(text)) {
      hasSectionBanner = true;
    }

    if (hasMarker && hasSectionBanner) {
      return true;
    }
  }

  return false;
}

/** Try to auto-detect hs_err content and set the language mode. */
function detectAndSetLanguage(document: vscode.TextDocument): void {
  if (document.languageId === "hserr") {
    return;
  }
  if (looksLikeHsErr(document)) {
    vscode.languages.setTextDocumentLanguage(document, "hserr");
  }
}

/**
 * Check raw file bytes for hs_err markers without opening a full TextDocument.
 * Returns true if the file looks like an hs_err / VM.info report.
 */
function looksLikeHsErrRaw(content: string): boolean {
  const lines = content.split(/\r?\n/, 150);
  let hasMarker = false;
  let hasBanner = false;
  for (const line of lines) {
    if (line.includes(BUG_REPORT_URL)) { hasMarker = true; }
    if (/^#\s+(?:JRE version:|Java VM:)/.test(line)) { hasMarker = true; }
    if (BANNER_RE.test(line)) { hasBanner = true; }
    if (hasMarker && hasBanner) { return true; }
  }
  return false;
}

/**
 * Simple string hash (djb2) for cache keys.
 */
function hashString(s: string): number {
  let h = 5381;
  for (let i = 0; i < s.length; i++) {
    h = ((h << 5) + h + s.charCodeAt(i)) | 0;
  }
  return h >>> 0;
}

/** Cache key for workspace-level detection results. */
const CACHE_KEY = "hserr.detectionCache";

interface DetectionCache {
  [key: string]: boolean; // hash of first 200 chars → isHsErr
}

/**
 * Scan workspace for .log and .txt files that might be hs_err reports.
 * Uses a cache (hash of first 200 chars → result) stored in workspaceState
 * to avoid re-reading files that haven't changed.
 */
async function scanWorkspaceFiles(state: vscode.Memento): Promise<void> {
  const cache: DetectionCache = state.get<DetectionCache>(CACHE_KEY, {});
  const newCache: DetectionCache = {};

  const uris = await vscode.workspace.findFiles("**/*.{log,txt}", undefined, 200);
  for (const uri of uris) {
    try {
      const bytes = await vscode.workspace.fs.readFile(uri);
      // Read first 200 chars for cache key, first 8KB for detection
      const prefix = String.fromCharCode(...bytes.slice(0, 200));
      const key = String(hashString(prefix));

      let isHsErr: boolean;
      if (key in cache) {
        isHsErr = cache[key];
      } else {
        const slice = bytes.byteLength > 8192 ? bytes.slice(0, 8192) : bytes;
        const head = String.fromCharCode(...slice);
        isHsErr = looksLikeHsErrRaw(head);
      }
      newCache[key] = isHsErr;

      if (isHsErr) {
        const doc = await vscode.workspace.openTextDocument(uri);
        if (doc.languageId !== "hserr") {
          await vscode.languages.setTextDocumentLanguage(doc, "hserr");
        }
      }
    } catch {
      // Skip files that can't be read
    }
  }

  await state.update(CACHE_KEY, newCache);
}

// ---------------------------------------------------------------------------
// Activation
// ---------------------------------------------------------------------------

export function activate(context: vscode.ExtensionContext) {
  const selector: vscode.DocumentSelector = { language: "hserr" };

  context.subscriptions.push(
    vscode.languages.registerDocumentSymbolProvider(
      selector,
      new HserrDocumentSymbolProvider()
    ),
    vscode.languages.registerFoldingRangeProvider(
      selector,
      new HserrFoldingRangeProvider()
    )
  );

  // Content-based detection: check all already-loaded documents
  for (const doc of vscode.workspace.textDocuments) {
    detectAndSetLanguage(doc);
  }

  // Content-based detection: check newly opened documents and tab switches
  context.subscriptions.push(
    vscode.workspace.onDidOpenTextDocument((doc) => {
      detectAndSetLanguage(doc);
    }),
    vscode.window.onDidChangeActiveTextEditor((editor) => {
      if (editor) {
        detectAndSetLanguage(editor.document);
      }
    })
  );

  // Scan workspace files for hs_err reports (sets icons in explorer)
  scanWorkspaceFiles(context.workspaceState);
}

export function deactivate() {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** End position of a line (line number, last char). */
function lineEnd(document: vscode.TextDocument, line: number): vscode.Position {
  return new vscode.Position(line, document.lineAt(line).text.length);
}

/** Max chars for inline detail shown in the outline. */
const MAX_DETAIL = 80;

/** Truncate a string, appending … if it exceeds `max` chars. */
function truncate(s: string, max = MAX_DETAIL): string {
  return s.length <= max ? s : s.slice(0, max) + "…";
}

/** Close the range of a DocumentSymbol so it extends from its start to `endLine`. */
function closeRange(
  sym: vscode.DocumentSymbol,
  document: vscode.TextDocument,
  endLine: number
): void {
  sym.range = new vscode.Range(sym.range.start, lineEnd(document, endLine));
}

/**
 * Detect a sub-section heading from a line of text.
 * Returns [label, inlineContent] or `undefined`.
 * `inlineContent` is the text after the label on the same line (may be empty).
 */
function matchSubsection(text: string): [string, string] | undefined {
  /** Helper: return [label, rest-of-line] given a label we matched. */
  function hit(label: string): [string, string] {
    const rest = text.slice(label.length).trim();
    return [label, rest];
  }

  // Standalone heading  e.g. "Registers:"
  const solo = SUBSECTION_RE.exec(text);
  if (solo) {
    if (SUPPRESSED_HEADINGS.has(solo[1])) {
      return undefined;
    }
    return hit(solo[1]);
  }

  // Event log heading  e.g. "Events (1000 events):"
  const ev = EVENT_LOG_RE.exec(text);
  if (ev) {
    return hit(ev[1]);
  }

  // Lower-case system headings  e.g. "uname: ...", "libc: ..."
  for (const lc of SYSTEM_HEADINGS_LC) {
    if (text.startsWith(lc)) {
      return hit(lc);
    }
  }

  // rlimit (soft/hard):
  if (RLIMIT_RE.test(text)) {
    return hit("rlimit:");
  }

  // load average:
  if (LOAD_AVG_RE.test(text)) {
    return ["load average:", text.slice("load average:".length).trim()];
  }

  // OS uptime:
  if (OS_UPTIME_RE.test(text)) {
    return ["OS uptime:", text.slice("OS uptime:".length).trim()];
  }

  // container (cgroup) information:
  if (CONTAINER_RE.test(text)) {
    return hit("container (cgroup) information:");
  }

  // Process Memory:
  if (PROCESS_MEMORY_RE.test(text)) {
    return hit("Process Memory:");
  }

  // /proc/meminfo, /sys/kernel/... etc.
  const procMatch = PROC_PATH_RE.exec(text);
  if (procMatch) {
    return hit(procMatch[1]);
  }

  // CPU Model and flags from /proc/cpuinfo:
  if (CPU_MODEL_RE.test(text)) {
    return hit("CPU Model and flags:");
  }

  // NUMA node information:
  if (NUMA_INFO_RE.test(text)) {
    return hit("NUMA node information:");
  }

  // NUMA node N:  (individual nodes)
  const numaMatch = NUMA_NODE_RE.exec(text);
  if (numaMatch) {
    const label = numaMatch[1] + ":";
    return [label, text.slice(numaMatch[0].length).trim()];
  }

  // Steal ticks ...
  if (STEAL_TICKS_RE.test(text)) {
    return hit("Steal ticks:");
  }

  // Special multi-word headings with inline data
  const inl = SUBSECTION_INLINE_RE.exec(text);
  if (inl) {
    const label = inl[1];
    // Accept if it's in our explicit set
    if (INLINE_HEADINGS.has(label)) {
      return hit(label);
    }
    // Accept any single-word Title-case label with inline data  (e.g. "Heap: ...")
    if (/^[A-Z]\w+:$/.test(label) && !SUPPRESSED_HEADINGS.has(label)) {
      return hit(label);
    }
  }

  return undefined;
}

// ---------------------------------------------------------------------------
// Document Symbol Provider  (Outline / Breadcrumbs)
// ---------------------------------------------------------------------------

class HserrDocumentSymbolProvider implements vscode.DocumentSymbolProvider {
  provideDocumentSymbols(
    document: vscode.TextDocument
  ): vscode.DocumentSymbol[] {
    const symbols: vscode.DocumentSymbol[] = [];
    let currentSection: vscode.DocumentSymbol | undefined;
    let lastChild: vscode.DocumentSymbol | undefined;
    let headerDescription = "";
    /** True while inside a /proc/ or /sys/ or /etc/ content block. */
    let inProcBlock = false;

    /** Close the previous child span up to `endLine`. */
    const closePrevChild = (endLine: number) => {
      if (lastChild && endLine >= lastChild.range.start.line) {
        closeRange(lastChild, document, endLine);
      }
      lastChild = undefined;
    };

    /** Close the previous section (and its last child) up to `endLine`. */
    const closePrevSection = (endLine: number) => {
      closePrevChild(endLine);
      if (currentSection) {
        closeRange(currentSection, document, endLine);
      }
      currentSection = undefined;
    };

    /** Push a child symbol under the current section. */
    const pushChild = (
      name: string,
      detail: string,
      kind: vscode.SymbolKind,
      line: vscode.TextLine
    ) => {
      if (!currentSection) {
        return;
      }
      // Close previous child to line before this one
      if (lastChild && line.lineNumber > lastChild.range.start.line) {
        closeRange(lastChild, document, line.lineNumber - 1);
      }
      const child = new vscode.DocumentSymbol(
        name,
        detail,
        kind,
        line.range,
        line.range
      );
      currentSection.children.push(child);
      lastChild = child;
    };

    for (let i = 0; i < document.lineCount; i++) {
      const line = document.lineAt(i);
      const text = line.text;

      // --- Major section banners ---
      const bannerMatch = BANNER_RE.exec(text);
      if (bannerMatch) {
        inProcBlock = false;
        closePrevSection(i > 0 ? i - 1 : i);
        const name = bannerMatch[1].replace(/\s+/g, " ");
        currentSection = new vscode.DocumentSymbol(
          name,
          "",
          vscode.SymbolKind.Namespace,
          line.range,
          line.range
        );
        symbols.push(currentSection);
        continue;
      }

      // --- Header section (lines starting with #) ---
      // For VM.info output, there may be preamble lines before the first #.
      // Detect # header block wherever it starts (before first banner).
      if (!currentSection && text.startsWith("#")) {
        currentSection = new vscode.DocumentSymbol(
          "Header",
          "",
          vscode.SymbolKind.Namespace,
          line.range,
          line.range
        );
        symbols.push(currentSection);
        continue;
      }

      // Extract error type and problematic frame from header for the description
      if (currentSection?.name === "Header") {
        const errMatch = HEADER_ERROR_RE.exec(text);
        if (errMatch && !headerDescription) {
          headerDescription = errMatch[1];
          currentSection.detail = headerDescription;
        }
        const frameMatch = HEADER_FRAME_RE.exec(text);
        if (frameMatch) {
          let func = frameMatch[1];
          // Strip trailing (args) or JVM signature
          func = func.replace(/\(.*$/, "");
          // For J frames: strip leading compile-id and tier
          func = func.replace(/^\d+\s+(?:c[12]\s+)?/, "").trim();
          if (headerDescription) {
            currentSection.detail = `${headerDescription} at ${func}`;
          } else {
            currentSection.detail = func;
          }
        }
      }

      // --- END marker ---
      if (/^END\.\s*$/.test(text)) {
        closePrevSection(i > 0 ? i - 1 : i);
        symbols.push(
          new vscode.DocumentSymbol(
            "END",
            "",
            vscode.SymbolKind.Constant,
            line.range,
            line.range
          )
        );
        continue;
      }

      // --- Special headings ---

      // Current thread
      const threadMatch = CURRENT_THREAD_RE.exec(text);
      if (threadMatch) {
        pushChild(
          "Current thread",
          threadMatch[1],
          vscode.SymbolKind.Event,
          line
        );
        continue;
      }

      // Current CompileTask:
      if (COMPILE_TASK_RE.test(text)) {
        pushChild("Current CompileTask:", "", vscode.SymbolKind.Event, line);
        continue;
      }

      // siginfo:
      if (SIGINFO_RE.test(text)) {
        const brief = text.replace(/^siginfo:\s*/, "").slice(0, 60);
        pushChild("siginfo:", brief, vscode.SymbolKind.Event, line);
        continue;
      }

      // VM_Operation
      if (VM_OPERATION_RE.test(text)) {
        pushChild("VM_Operation", "", vscode.SymbolKind.Event, line);
        continue;
      }

      // --- GC invocation entries (update parent detail, don't list individually) ---
      const gcMatch = GC_INVOCATION_RE.exec(text);
      if (gcMatch && lastChild) {
        lastChild.detail = `${gcMatch[2]} GCs (${gcMatch[3]} full)`;
        continue;
      }

      // --- Generic sub-section headings ---
      if (!inProcBlock) {
        const subMatch = matchSubsection(text);
        if (subMatch && currentSection) {
          const [label, inline] = subMatch;
          if (PROC_PATH_RE.test(text)) {
            inProcBlock = true;
          }
          pushChild(label, truncate(inline), vscode.SymbolKind.Field, line);
          continue;
        }
      } else if (text.trim() === "") {
        inProcBlock = false;
      }
    }

    // Close last section
    closePrevSection(document.lineCount - 1);

    return symbols;
  }
}

// ---------------------------------------------------------------------------
// Folding Range Provider
// ---------------------------------------------------------------------------

class HserrFoldingRangeProvider implements vscode.FoldingRangeProvider {
  provideFoldingRanges(
    document: vscode.TextDocument
  ): vscode.FoldingRange[] {
    const ranges: vscode.FoldingRange[] = [];

    let sectionStart = -1;
    let subsectionStart = -1;
    let headerStart = -1;
    let codeBlockStart = -1;
    /** True while inside a /proc/ or /sys/ or /etc/ content block. */
    let inProcBlock = false;

    for (let i = 0; i < document.lineCount; i++) {
      const text = document.lineAt(i).text;

      // --- Major section banners ---
      if (BANNER_RE.test(text)) {
        inProcBlock = false;
        // Close previous subsection and section
        if (subsectionStart >= 0 && i - 1 > subsectionStart) {
          ranges.push(
            new vscode.FoldingRange(subsectionStart, i - 1, vscode.FoldingRangeKind.Region)
          );
          subsectionStart = -1;
        }
        if (sectionStart >= 0 && i - 1 > sectionStart) {
          ranges.push(
            new vscode.FoldingRange(sectionStart, i - 1, vscode.FoldingRangeKind.Region)
          );
        }
        sectionStart = i;
        continue;
      }

      // --- Header block (# lines) ---
      // For VM.info output, the # header may not start at line 0.
      if (headerStart < 0 && sectionStart < 0 && text.startsWith("#")) {
        headerStart = i;
        continue;
      }
      if (headerStart >= 0 && !text.startsWith("#") && text.trim() === "") {
        // End of header block
        if (i - 1 > headerStart) {
          ranges.push(
            new vscode.FoldingRange(headerStart, i - 1, vscode.FoldingRangeKind.Comment)
          );
        }
        headerStart = -1;
      }

      // --- Code-blob horizontal rules (80+ dashes) ---
      if (HLINE_RE.test(text)) {
        if (codeBlockStart >= 0) {
          // Closing rule
          if (i > codeBlockStart) {
            ranges.push(
              new vscode.FoldingRange(codeBlockStart, i, vscode.FoldingRangeKind.Region)
            );
          }
          codeBlockStart = -1;
        } else {
          codeBlockStart = i;
        }
        continue;
      }

      // --- Sub-section headings ---
      if (inProcBlock) {
        if (text.trim() === "") {
          inProcBlock = false;
        }
      } else {
      const isSub =
        matchSubsection(text) !== undefined ||
        CURRENT_THREAD_RE.test(text) ||
        SIGINFO_RE.test(text) ||
        COMPILE_TASK_RE.test(text) ||
        VM_OPERATION_RE.test(text);

      if (isSub) {
        if (PROC_PATH_RE.test(text)) {
          inProcBlock = true;
        }
        // Close previous subsection
        if (subsectionStart >= 0 && i - 1 > subsectionStart) {
          ranges.push(
            new vscode.FoldingRange(subsectionStart, i - 1, vscode.FoldingRangeKind.Region)
          );
        }
        subsectionStart = i;
      }
      }
    }

    // Close trailing ranges
    const last = document.lineCount - 1;
    if (headerStart >= 0 && last > headerStart) {
      ranges.push(
        new vscode.FoldingRange(headerStart, last, vscode.FoldingRangeKind.Comment)
      );
    }
    if (subsectionStart >= 0 && last > subsectionStart) {
      ranges.push(
        new vscode.FoldingRange(subsectionStart, last, vscode.FoldingRangeKind.Region)
      );
    }
    if (sectionStart >= 0 && last > sectionStart) {
      ranges.push(
        new vscode.FoldingRange(sectionStart, last, vscode.FoldingRangeKind.Region)
      );
    }

    return ranges;
  }
}
