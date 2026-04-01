package me.bechberger.jhserr.web;

import me.bechberger.jhserr.HsErrJson;
import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.parser.HsErrParser;
import me.bechberger.jhserr.transform.RedactionConfig;
import me.bechberger.jhserr.transform.RedactionTransformer;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSObject;

/**
 * GraalVM Web Image entry point — wires Java parsing/redaction into the browser DOM.
 */
public class WebMain {

    private static HsErrReport currentReport;
    private static String originalText;
    private static String defaultConfigJson;

    // ── DOM references ──────────────────────────────────────────────────

    public static final JSObject DROP_ZONE = getElementById("drop-zone");
    public static final JSObject FILE_NAME_SPAN = getElementById("file-name");
    public static final JSObject SOURCE_OUTPUT = getElementById("source-output");
    public static final JSObject JSON_OUTPUT = getElementById("json-output");
    public static final JSObject REDACTED_OUTPUT = getElementById("redacted-output");
    public static final JSObject DIFF_OUTPUT = getElementById("diff-output");
    public static final JSObject STATUS = getElementById("status");
    public static final JSObject CONFIG_AREA = getElementById("config-area");
    public static final JSObject DL_JSON_BTN = getElementById("dl-json");
    public static final JSObject DL_REDACTED_BTN = getElementById("dl-redacted");
    public static final JSObject COPY_JSON_BTN = getElementById("copy-json");
    public static final JSObject COPY_REDACTED_BTN = getElementById("copy-redacted");
    public static final JSObject APPLY_CONFIG_BTN = getElementById("apply-config");
    public static final JSObject RESET_CONFIG_BTN = getElementById("reset-config");
    public static final JSObject CONFIG_ERROR = getElementById("config-error");
    public static final JSObject REDACTION_SUMMARY = getElementById("redaction-summary");
    public static final JSObject TAB_SOURCE = getElementById("tab-source");
    public static final JSObject TAB_JSON = getElementById("tab-json");
    public static final JSObject TAB_REDACTED = getElementById("tab-redacted");
    public static final JSObject TAB_DIFF = getElementById("tab-diff");
    public static final JSObject PANEL_SOURCE = getElementById("panel-source");
    public static final JSObject PANEL_JSON = getElementById("panel-json");
    public static final JSObject PANEL_REDACTED = getElementById("panel-redacted");
    public static final JSObject PANEL_DIFF = getElementById("panel-diff");

    // ── entry point ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Generate default config once
        try {
            defaultConfigJson = new RedactionConfig().toJson();
        } catch (Exception e) {
            defaultConfigJson = "{}";
        }

        // Populate config from localStorage or defaults
        String saved = getLocalStorage("hserr-config");
        if (saved == null || saved.isEmpty()) {
            saved = defaultConfigJson;
        }
        setConfigValue(saved);

        // Wire events
        addEventListener(DROP_ZONE, "dragover", WebMain::onDragOver);
        addEventListener(DROP_ZONE, "dragleave", WebMain::onDragLeave);
        addEventListener(DROP_ZONE, "drop", WebMain::onDrop);
        addEventListener(DL_JSON_BTN, "click", e -> downloadJson());
        addEventListener(DL_REDACTED_BTN, "click", e -> downloadRedacted());
        addEventListener(COPY_JSON_BTN, "click", e -> copyJson());
        addEventListener(COPY_REDACTED_BTN, "click", e -> copyRedacted());
        addEventListener(APPLY_CONFIG_BTN, "click", e -> applyConfig());
        addEventListener(RESET_CONFIG_BTN, "click", e -> resetConfig());
        addEventListener(CONFIG_AREA, "input", e -> onConfigInput());
        addEventListener(SOURCE_OUTPUT, "input", e -> onSourceInput());
        addEventListener(TAB_SOURCE, "click", e -> showTab("source"));
        addEventListener(TAB_JSON, "click", e -> showTab("json"));
        addEventListener(TAB_REDACTED, "click", e -> showTab("redacted"));
        addEventListener(TAB_DIFF, "click", e -> showTab("diff"));

        setStatus("Drop an hs_err file or paste its contents into the Source tab.");
    }

    // ── drag & drop ─────────────────────────────────────────────────────

    private static void onDragOver(JSObject e) {
        preventDefault(e);
        addClass(DROP_ZONE, "drag-over");
    }

    private static void onDragLeave(JSObject e) {
        preventDefault(e);
        removeClass(DROP_ZONE, "drag-over");
    }

    private static void onDrop(JSObject e) {
        preventDefault(e);
        removeClass(DROP_ZONE, "drag-over");
        setStatus("Reading file…");

        JSObject dt = getJSProperty(e, "dataTransfer");
        JSObject files = getJSProperty(dt, "files");
        JSObject file = getArrayItem(files, 0);
        if (file == null) {
            setStatus("No file found in drop.");
            return;
        }

        String name = getStringProperty(file, "name");
        setProperty(FILE_NAME_SPAN, "textContent", name);
        setDisplay(FILE_NAME_SPAN, "inline");
        readFileText(file);
    }

    // Called from JS when FileReader finishes
    public static void onFileRead(String content) {
        runAsync(() -> processFile(content));
    }

    private static void processFile(String content) {
        try {
            setStatus("Parsing…");
            originalText = content;
            currentReport = HsErrParser.parse(content);

            // Source tab
               setSourceEditorValue(content);

            // JSON tab
            String json = HsErrJson.toJson(currentReport);
            setTextContent(JSON_OUTPUT, json);
            highlightElement(JSON_OUTPUT);

            redactAndShow();

            setStatus("Parsed successfully — " + content.split("\n", -1).length + " lines.");
            enableButtons(true);
            showTab("source");
        } catch (Exception ex) {
            setStatus("Parse error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
               setSourceEditorValue(originalText != null ? originalText : "");
            setTextContent(JSON_OUTPUT, "");
            setTextContent(REDACTED_OUTPUT, "");
            setTextContent(DIFF_OUTPUT, "");
            enableButtons(false);
        }
    }

    // ── redaction ───────────────────────────────────────────────────────

    private static void redactAndShow() {
        if (currentReport == null) return;
        try {
            String configJson = getConfigValue();
            RedactionConfig config = RedactionConfig.fromJson(configJson);
            setTextContent(CONFIG_ERROR, ""); // clear previous errors
            RedactionTransformer transformer = new RedactionTransformer(config);
            HsErrReport redacted = transformer.transform(currentReport);
            String redactedText = redacted.toString();

            // Redaction summary (only applied changes)
            buildRedactionSummary(config, originalText, redactedText, transformer);

            // Redacted tab with inline change highlights
            buildRedactedView(originalText, redactedText);

            // Diff tab
            if (originalText != null) {
                buildDiff(originalText, redactedText);
            }
        } catch (Exception ex) {
            setTextContent(CONFIG_ERROR, "Config error: " + ex.getMessage());
            setTextContent(REDACTED_OUTPUT, "Redaction error: " + ex.getMessage());
            setTextContent(DIFF_OUTPUT, "");
        }
    }

    private static void buildRedactionSummary(RedactionConfig config, String original, String redacted, RedactionTransformer transformer) {
        if (original == null || original.equals(redacted)) {
            setInnerHTML(REDACTION_SUMMARY, "<span class=\"count\">0</span> redactions");
            return;
        }
        // Count actual changed lines
        String[] origLines = original.split("\n", -1);
        String[] redLines = redacted.split("\n", -1);
        int changed = 0;
        int limit = Math.min(origLines.length, redLines.length);
        for (int i = 0; i < limit; i++) {
            if (!origLines[i].equals(redLines[i])) changed++;
        }
        changed += Math.abs(origLines.length - redLines.length);

        StringBuilder sb = new StringBuilder();
        sb.append("<span class=\"count\">").append(changed).append("</span> line")
          .append(changed != 1 ? "s" : "").append(" changed");
        if (config.redactUsernames() && !transformer.usernames().isEmpty()) {
            sb.append(" · Users: ").append(String.join(", ", transformer.usernames()));
        }
        if (config.redactHostnames() && !transformer.hostnames().isEmpty()) {
            sb.append(" · Hosts: ").append(String.join(", ", transformer.hostnames()));
        }
        setInnerHTML(REDACTION_SUMMARY, sb.toString());
    }

    /** Build redacted view with changed portions highlighted inline. */
    private static void buildRedactedView(String original, String redactedText) {
        if (original == null) {
            setTextContent(REDACTED_OUTPUT, redactedText);
            return;
        }
        String[] origLines = original.split("\n", -1);
        String[] redLines = redactedText.split("\n", -1);
        int max = Math.max(origLines.length, redLines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            String o = i < origLines.length ? origLines[i] : "";
            String r = i < redLines.length ? redLines[i] : "";
            if (o.equals(r)) {
                sb.append(escapeHtml(r)).append('\n');
            } else {
                highlightInlineChanges(sb, o, r);
                sb.append('\n');
            }
        }
        setInnerHTML(REDACTED_OUTPUT, sb.toString());
    }

    /** Highlight only the changed substrings within a redacted line.
     *  Uses common-prefix / common-suffix peeling to find each changed region,
     *  then emits a tooltip showing the original text. */
    private static void highlightInlineChanges(StringBuilder sb, String orig, String redacted) {
        // Find common prefix
        int prefixLen = 0;
        int minLen = Math.min(orig.length(), redacted.length());
        while (prefixLen < minLen && orig.charAt(prefixLen) == redacted.charAt(prefixLen)) {
            prefixLen++;
        }
        // Find common suffix (not overlapping with prefix)
        int suffixLen = 0;
        while (suffixLen < (minLen - prefixLen)
                && orig.charAt(orig.length() - 1 - suffixLen) == redacted.charAt(redacted.length() - 1 - suffixLen)) {
            suffixLen++;
        }
        // Emit: unchanged prefix + highlighted middle (with tooltip) + unchanged suffix
        sb.append(escapeHtml(redacted.substring(0, prefixLen)));
        String origMiddle = orig.substring(prefixLen, orig.length() - suffixLen);
        String redMiddle = redacted.substring(prefixLen, redacted.length() - suffixLen);
        if (!redMiddle.isEmpty()) {
            sb.append("<span class=\"redact-mark\" title=\"").append(escapeHtmlAttr(origMiddle)).append("\">");
            sb.append(escapeHtml(redMiddle)).append("</span>");
        }
        if (suffixLen > 0) {
            sb.append(escapeHtml(redacted.substring(redacted.length() - suffixLen)));
        }
    }

    private static String escapeHtmlAttr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Build a line-level diff showing only changed lines with context. */
    private static void buildDiff(String original, String redacted) {
        String[] origLines = original.split("\n", -1);
        String[] redLines = redacted.split("\n", -1);
        int max = Math.max(origLines.length, redLines.length);
        int context = 3;

        // Mark which lines have changes
        boolean[] changed = new boolean[max];
        for (int i = 0; i < max; i++) {
            String o = i < origLines.length ? origLines[i] : "";
            String r = i < redLines.length ? redLines[i] : "";
            changed[i] = !o.equals(r);
        }

        // Expand to include context lines
        boolean[] visible = new boolean[max];
        for (int i = 0; i < max; i++) {
            if (changed[i]) {
                for (int j = Math.max(0, i - context); j <= Math.min(max - 1, i + context); j++) {
                    visible[j] = true;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean anyChanges = false;
        for (int i = 0; i < max; i++) {
            if (changed[i]) { anyChanges = true; break; }
        }
        if (!anyChanges) {
            setInnerHTML(DIFF_OUTPUT, "<span class=\"diff-skip\">No differences — source and redacted output are identical.</span>");
            return;
        }

        boolean lastWasSkip = false;
        for (int i = 0; i < max; i++) {
            if (!visible[i]) {
                if (!lastWasSkip) {
                    sb.append("<span class=\"diff-skip\">  …</span>");
                    lastWasSkip = true;
                }
                continue;
            }
            lastWasSkip = false;
            String o = i < origLines.length ? origLines[i] : "";
            String r = i < redLines.length ? redLines[i] : "";
            if (changed[i]) {
                sb.append("<span class=\"diff-del\">- ").append(escapeHtml(o)).append("</span>");
                sb.append("<span class=\"diff-add\">+ ").append(escapeHtml(r)).append("</span>");
            } else {
                sb.append("<span class=\"diff-ctx\">  ").append(escapeHtml(o)).append("</span>\n");
            }
        }
        setInnerHTML(DIFF_OUTPUT, sb.toString());
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void onSourceInput() {
           String content = getSourceEditorValue();
        if (content == null || content.isEmpty()) return;
        // Show a name hint when text is pasted directly
        if (originalText == null || originalText.isEmpty()) {
            setProperty(FILE_NAME_SPAN, "textContent", "(pasted)");
            setDisplay(FILE_NAME_SPAN, "inline");
        }
        debounce(() -> processFile(content), 500);
    }

    private static void onConfigInput() {
        if (currentReport == null) return;
        String configJson = getConfigValue();
        setLocalStorage("hserr-config", configJson);
        debounce(WebMain::redactAndShow, 300);
    }

    private static void applyConfig() {
        String configJson = getConfigValue();
        setLocalStorage("hserr-config", configJson);
        redactAndShow();
        setStatus("Config applied and saved.");
    }

    private static void resetConfig() {
        setConfigValue(defaultConfigJson);
        setLocalStorage("hserr-config", defaultConfigJson);
        redactAndShow();
        setStatus("Config reset to defaults.");
    }

    // ── downloads ───────────────────────────────────────────────────────

    private static void downloadJson() {
        String json = getStringProperty(JSON_OUTPUT, "textContent");
        triggerDownload("hserr.json", json, "application/json");
    }

    private static void downloadRedacted() {
        String text = getStringProperty(REDACTED_OUTPUT, "textContent");
        triggerDownload("hserr_redacted.log", text, "text/plain");
    }

    private static void copyJson() {
        String json = getStringProperty(JSON_OUTPUT, "textContent");
        copyToClipboard(json, COPY_JSON_BTN);
    }

    private static void copyRedacted() {
        String text = getStringProperty(REDACTED_OUTPUT, "textContent");
        copyToClipboard(text, COPY_REDACTED_BTN);
    }

    // ── tab switching ───────────────────────────────────────────────────

    private static void showTab(String tab) {
        String[] names = {"source", "json", "redacted", "diff"};
        JSObject[] tabs = {TAB_SOURCE, TAB_JSON, TAB_REDACTED, TAB_DIFF};
        JSObject[] panels = {PANEL_SOURCE, PANEL_JSON, PANEL_REDACTED, PANEL_DIFF};
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(tab)) {
                addClass(tabs[i], "active");
                setDisplay(panels[i], "block");
            } else {
                removeClass(tabs[i], "active");
                setDisplay(panels[i], "none");
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static void setStatus(String msg) {
        setProperty(STATUS, "textContent", msg);
    }

    private static void enableButtons(boolean on) {
        DL_JSON_BTN.set("disabled", JSBoolean.of(!on));
        DL_REDACTED_BTN.set("disabled", JSBoolean.of(!on));
        COPY_JSON_BTN.set("disabled", JSBoolean.of(!on));
        COPY_REDACTED_BTN.set("disabled", JSBoolean.of(!on));
    }

    // ── JS bridge methods ───────────────────────────────────────────────

    @JS.Coerce
    @JS("return document.getElementById(id);")
    public static native JSObject getElementById(String id);

    @JS.Coerce
    @JS("return obj[prop];")
    public static native String getStringProperty(JSObject obj, String prop);

    @JS.Coerce
    @JS("return obj[prop];")
    public static native JSObject getJSProperty(JSObject obj, String prop);

    @JS.Coerce
    @JS("obj[prop] = value;")
    public static native void setProperty(JSObject obj, String prop, String value);

    @JS.Coerce
    @JS("el.textContent = text;")
    public static native void setTextContent(JSObject el, String text);

    @JS.Coerce
    @JS("e.preventDefault(); e.stopPropagation();")
    public static native void preventDefault(JSObject e);

    @JS.Coerce
    @JS("el.classList.add(cls);")
    public static native void addClass(JSObject el, String cls);

    @JS.Coerce
    @JS("el.classList.remove(cls);")
    public static native void removeClass(JSObject el, String cls);

    @JS.Coerce
    @JS("el.style.display = value;")
    public static native void setDisplay(JSObject el, String value);

    @JS.Coerce
    @JS("el.innerHTML = html;")
    public static native void setInnerHTML(JSObject el, String html);

    @JS.Coerce
    @JS("if (typeof hljs !== 'undefined') { el.removeAttribute('data-highlighted'); hljs.highlightElement(el); }")
    public static native void highlightElement(JSObject el);

    @JS.Coerce
    @JS("o.addEventListener(event, (e) => handler(e));")
    static native void addEventListenerImpl(JSObject o, String event, EventHandler handler);

    static void addEventListener(JSObject o, String event, EventHandler handler) {
        addEventListenerImpl(o, event, e -> {
            try {
                handler.handleEvent(e);
            } catch (Throwable t) {
                System.err.println("Error in event handler: " + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    @JS.Coerce
    @JS("setTimeout(r, 0);")
    private static native void runAsync(Runnable r);

    @JS.Coerce
    @JS("""
        if (typeof window.__hserr_debounce_timer !== 'undefined') {
            clearTimeout(window.__hserr_debounce_timer);
        }
        window.__hserr_debounce_timer = setTimeout(fn, ms);
        """)
    private static native void debounce(Runnable fn, int ms);

    @JS.Coerce
    @JS("var v = localStorage.getItem(key); return v === null ? '' : v;")
    private static native String getLocalStorage(String key);

    @JS.Coerce
    @JS("localStorage.setItem(key, value);")
    private static native void setLocalStorage(String key, String value);

    @JS.Coerce
    @JS("return window.cmConfigEditor ? window.cmConfigEditor.getValue() : document.getElementById('config-area').value;")
    private static native String getConfigValue();

    @JS.Coerce
    @JS("if (window.cmConfigEditor) { window.cmConfigEditor.setValue(value); } else { document.getElementById('config-area').value = value; }")
    private static native void setConfigValue(String value);

    @JS.Coerce
    @JS("return window.cmSourceEditor ? window.cmSourceEditor.getValue() : document.getElementById('source-output').value;")
    private static native String getSourceEditorValue();

    @JS.Coerce
    @JS("if (window.cmSourceEditor) { window._cmSourceSuppressChange = true; window.cmSourceEditor.setValue(value); window._cmSourceSuppressChange = false; } else { document.getElementById('source-output').value = value; }")
    private static native void setSourceEditorValue(String value);

    @JS.Coerce
    @JS("return arr[idx];")
    private static native JSObject getArrayItem(JSObject arr, int idx);

    @JS.Coerce
    @JS("""
        var reader = new FileReader();
        reader.onload = function(ev) {
            callback(ev);
        };
        reader.readAsText(file);
        """)
    private static native void readFileTextImpl(JSObject file, EventHandler callback);

    private static void readFileText(JSObject file) {
        readFileTextImpl(file, ev -> {
            JSObject target = getJSProperty(ev, "target");
            String result = getStringProperty(target, "result");
            onFileRead(result);
        });
    }

    @JS.Coerce
    @JS("""
        var blob = new Blob([content], {type: mimeType});
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        """)
    private static native void triggerDownload(String filename, String content, String mimeType);

    @JS.Coerce
    @JS("""
        navigator.clipboard.writeText(text).then(function() {
            var orig = btn.textContent;
            btn.textContent = '\u2713 Copied!';
            setTimeout(function() { btn.textContent = orig; }, 1500);
        });
        """)
    private static native void copyToClipboard(String text, JSObject btn);
}

@FunctionalInterface
interface EventHandler {
    void handleEvent(JSObject event);
}


