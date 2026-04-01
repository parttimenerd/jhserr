import * as vscode from "vscode";

/** Regex for major section banners like  ---------------  T H R E A D  --------------- */
const BANNER_RE = /^-{10,}\s+(.+?)\s+-{10,}$/;

/** Regex for sub-section headings that end with a colon on their own line */
const SUBSECTION_RE = /^([A-Z][\w /()]+:)\s*$/;

/** Regex for sub-section headings with inline content (e.g. "Heap:" or "CPU: ...") */
const SUBSECTION_INLINE_RE = /^([A-Z][\w /()]+:)\s+/;

/** Known subsection heading labels that appear with inline content */
const INLINE_HEADINGS = new Set([
  "Stack:",
  "Memory:",
  "CPU:",
  "vm_info:",
  "Card table byte_map:",
]);

export function activate(context: vscode.ExtensionContext) {
  context.subscriptions.push(
    vscode.languages.registerDocumentSymbolProvider(
      { language: "hserr" },
      new HserrDocumentSymbolProvider()
    )
  );
}

export function deactivate() {}

class HserrDocumentSymbolProvider implements vscode.DocumentSymbolProvider {
  provideDocumentSymbols(
    document: vscode.TextDocument
  ): vscode.DocumentSymbol[] {
    const symbols: vscode.DocumentSymbol[] = [];
    let currentSection: vscode.DocumentSymbol | undefined;

    for (let i = 0; i < document.lineCount; i++) {
      const line = document.lineAt(i);
      const text = line.text;

      // Major section banners
      const bannerMatch = BANNER_RE.exec(text);
      if (bannerMatch) {
        // Close previous section range
        if (currentSection) {
          currentSection.range = new vscode.Range(
            currentSection.range.start,
            new vscode.Position(i - 1, document.lineAt(i - 1).text.length)
          );
        }
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

      // Header section (lines starting with #) — treat as first section
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

      // END marker
      if (/^END\.\s*$/.test(text)) {
        if (currentSection) {
          currentSection.range = new vscode.Range(
            currentSection.range.start,
            new vscode.Position(i - 1, document.lineAt(i - 1).text.length)
          );
        }
        const endSym = new vscode.DocumentSymbol(
          "END",
          "",
          vscode.SymbolKind.Constant,
          line.range,
          line.range
        );
        symbols.push(endSym);
        continue;
      }

      // Sub-section headings
      const subMatch =
        SUBSECTION_RE.exec(text) ||
        (INLINE_HEADINGS.has(text.split(/\s/)[0]) || SUBSECTION_INLINE_RE.exec(text)
          ? SUBSECTION_INLINE_RE.exec(text)
          : null);
      if (subMatch && currentSection) {
        const child = new vscode.DocumentSymbol(
          subMatch[1],
          "",
          vscode.SymbolKind.Field,
          line.range,
          line.range
        );
        currentSection.children.push(child);
      }
    }

    // Close last section
    if (currentSection) {
      const lastLine = document.lineCount - 1;
      currentSection.range = new vscode.Range(
        currentSection.range.start,
        new vscode.Position(lastLine, document.lineAt(lastLine).text.length)
      );
    }

    return symbols;
  }
}
