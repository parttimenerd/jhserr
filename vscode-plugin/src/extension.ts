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

/** Problematic frame (V/C/J/j/v frame line in header) */
const HEADER_FRAME_RE = /^#\s+([VvCJjA])\s+/;

// ---------------------------------------------------------------------------
// Content detection
// ---------------------------------------------------------------------------

/** The bug-report URL that appears in every hs_err header. */
const BUG_REPORT_URL = "https://bugreport.java.com/bugreport/crash.jsp";

/**
 * Heuristic content-based detection for hs_err files.
 * Requires BOTH:
 *  1. The characteristic bug-report URL in the # header, AND
 *  2. A spaced-letter section banner (e.g. ---  T H R E A D  ---).
 */
function looksLikeHsErr(document: vscode.TextDocument): boolean {
  const lang = document.languageId;
  if (lang !== "plaintext" && lang !== "log") {
    return false;
  }

  const scanLines = Math.min(document.lineCount, 150);
  let hasBugReportUrl = false;
  let hasSectionBanner = false;

  for (let i = 0; i < scanLines; i++) {
    const text = document.lineAt(i).text;

    if (text.includes(BUG_REPORT_URL)) {
      hasBugReportUrl = true;
    }

    if (BANNER_RE.test(text)) {
      hasSectionBanner = true;
    }

    if (hasBugReportUrl && hasSectionBanner) {
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

  // Content-based detection: check already-open editors
  for (const editor of vscode.window.visibleTextEditors) {
    detectAndSetLanguage(editor.document);
  }

  // Content-based detection: check newly opened documents
  context.subscriptions.push(
    vscode.workspace.onDidOpenTextDocument((doc) => {
      detectAndSetLanguage(doc);
    })
  );
}

export function deactivate() {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** End position of a line (line number, last char). */
function lineEnd(document: vscode.TextDocument, line: number): vscode.Position {
  return new vscode.Position(line, document.lineAt(line).text.length);
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
 * Returns the heading label (including the colon) or `undefined`.
 */
function matchSubsection(text: string): string | undefined {
  // Standalone heading  e.g. "Registers:"
  const solo = SUBSECTION_RE.exec(text);
  if (solo) {
    return solo[1];
  }

  // Event log heading  e.g. "Events (1000 events):"
  const ev = EVENT_LOG_RE.exec(text);
  if (ev) {
    return ev[1];
  }

  // Special multi-word headings with inline data
  const inl = SUBSECTION_INLINE_RE.exec(text);
  if (inl) {
    const label = inl[1];
    // Accept if it's in our explicit set
    if (INLINE_HEADINGS.has(label)) {
      return label;
    }
    // Accept any single-word Title-case label with inline data  (e.g. "Heap: ...")
    if (/^[A-Z]\w+:$/.test(label)) {
      return label;
    }
  }

  // vm_info: starts lower-case — special handling
  if (text.startsWith("vm_info:")) {
    return "vm_info:";
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
      if (i === 0 && text.startsWith("#")) {
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

      // Extract error type from header for the description
      if (currentSection?.name === "Header") {
        const errMatch = HEADER_ERROR_RE.exec(text);
        if (errMatch && !headerDescription) {
          headerDescription = errMatch[1];
          currentSection.detail = headerDescription;
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

      // --- Generic sub-section headings ---
      const subLabel = matchSubsection(text);
      if (subLabel && currentSection) {
        pushChild(subLabel, "", vscode.SymbolKind.Field, line);
        continue;
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

    for (let i = 0; i < document.lineCount; i++) {
      const text = document.lineAt(i).text;

      // --- Major section banners ---
      if (BANNER_RE.test(text)) {
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

      // --- Header block (# lines at start) ---
      if (i === 0 && text.startsWith("#")) {
        headerStart = 0;
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
      const isSub =
        matchSubsection(text) !== undefined ||
        CURRENT_THREAD_RE.test(text) ||
        SIGINFO_RE.test(text) ||
        COMPILE_TASK_RE.test(text) ||
        VM_OPERATION_RE.test(text);

      if (isSub) {
        // Close previous subsection
        if (subsectionStart >= 0 && i - 1 > subsectionStart) {
          ranges.push(
            new vscode.FoldingRange(subsectionStart, i - 1, vscode.FoldingRangeKind.Region)
          );
        }
        subsectionStart = i;
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
