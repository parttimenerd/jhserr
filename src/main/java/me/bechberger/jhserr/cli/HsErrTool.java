package me.bechberger.jhserr.cli;

import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.HsErrJson;
import me.bechberger.jhserr.parser.HsErrParser;
import me.bechberger.jhserr.transform.*;
import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "hserr",
        description = {"Parse, transform and redact HotSpot error report (hs_err) files."},
        version = "jhserr 0.0.0",
    subcommands = {HsErrTool.RedactCmd.class, HsErrTool.JsonCmd.class, HsErrTool.RoundTripCmd.class},
        mixinStandardHelpOptions = true)
public class HsErrTool implements Runnable {

    /** Read file contents, or stdin when path is "-". */
    static String readInput(String path) throws IOException {
        if ("-".equals(path)) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return Files.readString(Path.of(path));
    }

    @Override
    public void run() {
        // no subcommand given — print help
        System.err.println("Usage: hserr <command> [options]");
        System.err.println("Commands: redact, json, roundtrip");
        System.err.println("Run 'hserr <command> --help' for more information.");
    }

    // ── redact ────────────────────────────────────────────────────────────

    @Command(name = "redact",
            description = {"Redact sensitive information from an hs_err file."},
            mixinStandardHelpOptions = true)
    public static class RedactCmd implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to the hs_err file (use - for stdin)")
        private String file;

        @Option(names = {"-o", "--output"}, description = "Write output to FILE instead of stdout")
        private Path outputFile;

        @Option(names = {"-c", "--config"}, description = "Path to redaction config JSON file")
        private Path configFile;

        @Option(names = {"--generate-config"}, description = "Print a default redaction config JSON and exit")
        private boolean generateConfig;

        @Option(names = {"--scan"}, description = "Scan for sensitive information only (no output)")
        private boolean scan;

        @Option(names = {"--json"}, description = "Output redacted report as JSON instead of text")
        private boolean json;

        @Option(names = {"--username"}, description = "Additional username to redact")
        private String[] usernames;

        @Option(names = {"--hostname"}, description = "Additional hostname to redact")
        private String[] hostnames;

        @Option(names = {"--hostname-ignore"}, description = "Hostname to exclude from redaction (false-positive)")
        private String[] hostnameIgnores;

        @Option(names = {"--sensitive-path"}, description = "Sensitive path prefix to redact")
        private String[] sensitivePaths;

        @Option(names = {"--minimal"}, description = "Minimal preset: redact usernames + hostnames only")
        private boolean minimal;

        @Option(names = {"--aggressive"}, description = "Aggressive preset: strip libs, events, thread names")
        private boolean aggressive;

        @Override
        public Integer call() throws Exception {
            if (generateConfig) {
                String configJson = new RedactionConfig().toJson();
                if (outputFile != null) {
                    Files.writeString(outputFile, configJson + "\n");
                    System.err.println("Config written to " + outputFile);
                } else {
                    System.out.println(configJson);
                }
                return 0;
            }

            HsErrReport report = HsErrParser.parse(readInput(file));

            if (scan) {
                RedactionEngine engine = new RedactionEngine();
                report.accept(engine);
                System.err.println("=== Redaction Findings ===");
                if (engine.findings().isEmpty()) {
                    System.err.println("No sensitive information found.");
                } else {
                    for (String f : engine.findings()) {
                        System.err.println("  - " + f);
                    }
                    if (!engine.usernames().isEmpty())
                        System.err.println("Usernames: " + engine.usernames());
                    if (!engine.hostnames().isEmpty())
                        System.err.println("Hostnames: " + engine.hostnames());
                }
                return 0;
            }

            RedactionConfig config = buildConfig();
            RedactionTransformer transformer = new RedactionTransformer(config);
            HsErrReport redacted = transformer.transform(report);

            PrintStream out = System.out;
            if (outputFile != null) {
                out = new PrintStream(Files.newOutputStream(outputFile));
            }
            try {
                if (json) {
                    out.println(HsErrJson.toJson(redacted));
                } else {
                    out.print(redacted);
                }
            } finally {
                if (outputFile != null) out.close();
            }

            if (!transformer.redactions().isEmpty()) {
                System.err.println("=== Redaction Summary ===");
                for (String r : transformer.redactions()) {
                    System.err.println("  - " + r);
                }
                if (!transformer.usernames().isEmpty())
                    System.err.println("Usernames redacted: " + transformer.usernames());
                if (!transformer.hostnames().isEmpty())
                    System.err.println("Hostnames redacted: " + transformer.hostnames());
            }
            return 0;
        }

        private RedactionConfig buildConfig() throws Exception {
            RedactionConfig config;
            if (configFile != null) {
                config = RedactionConfig.fromJsonFile(configFile);
            } else if (minimal) {
                config = RedactionConfig.minimal();
            } else if (aggressive) {
                config = RedactionConfig.aggressive();
            } else {
                config = new RedactionConfig();
            }
            if (usernames != null) {
                for (String u : usernames) config.addAdditionalUsername(u);
            }
            if (hostnames != null) {
                for (String h : hostnames) config.addAdditionalHostname(h);
            }
            if (hostnameIgnores != null) {
                for (String h : hostnameIgnores) config.addHostnameIgnore(h);
            }
            if (sensitivePaths != null) {
                for (String p : sensitivePaths) config.addSensitivePathPrefix(p);
            }
            return config;
        }
    }

    // ── json ──────────────────────────────────────────────────────────────

    @Command(name = "json",
            description = {"Convert between hs_err text and JSON formats."},
            subcommands = {JsonCmd.ToCmd.class, JsonCmd.FromCmd.class, JsonCmd.SchemaCmd.class},
            mixinStandardHelpOptions = true)
    public static class JsonCmd implements Runnable {
        @Override
        public void run() {
            System.err.println("Usage: hserr json <command>");
            System.err.println("Commands: to, from, schema");
        }

        @Command(name = "to",
                description = {"Convert an hs_err file to JSON."},
                mixinStandardHelpOptions = true)
        public static class ToCmd implements Callable<Integer> {
            @Parameters(index = "0", description = "Path to the hs_err file (use - for stdin)")
            private String file;

            @Option(names = {"-o", "--output"}, description = "Write JSON to FILE instead of stdout")
            private Path outputFile;

            @Override
            public Integer call() throws Exception {
                HsErrReport report = HsErrParser.parse(readInput(file));
                if (outputFile != null) {
                    HsErrJson.toJsonFile(report, outputFile);
                    System.err.println("JSON written to " + outputFile);
                } else {
                    System.out.println(HsErrJson.toJson(report));
                }
                return 0;
            }
        }

        @Command(name = "from",
                description = {"Convert a JSON file back to hs_err text format."},
                mixinStandardHelpOptions = true)
        public static class FromCmd implements Callable<Integer> {
            @Parameters(index = "0", description = "Path to the JSON file (use - for stdin)")
            private String file;

            @Option(names = {"-o", "--output"}, description = "Write hs_err text to FILE instead of stdout")
            private Path outputFile;

            @Override
            public Integer call() throws Exception {
                HsErrReport report = HsErrJson.fromJson(readInput(file));
                if (outputFile != null) {
                    Files.writeString(outputFile, report.toString());
                    System.err.println("Report written to " + outputFile);
                } else {
                    System.out.print(report);
                }
                return 0;
            }
        }

        @Command(name = "schema",
                description = {"Print the JSON schema for hs_err reports."},
                mixinStandardHelpOptions = true)
        public static class SchemaCmd implements Callable<Integer> {
            @Option(names = {"-o", "--output"}, description = "Write schema to FILE instead of stdout")
            private Path outputFile;

            @Override
            public Integer call() throws Exception {
                JacksonModule jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                        JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);
                SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                        SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .with(jacksonModule);
                SchemaGeneratorConfig config = configBuilder.build();
                SchemaGenerator generator = new SchemaGenerator(config);
                com.fasterxml.jackson.databind.JsonNode schema = generator.generateSchema(HsErrReport.class);
                String json = HsErrJson.mapper().writeValueAsString(schema);
                if (outputFile != null) {
                    Files.writeString(outputFile, json + "\n");
                    System.err.println("Schema written to " + outputFile);
                } else {
                    System.out.println(json);
                }
                return 0;
            }
        }
    }

    // ── roundtrip ───────────────────────────────────────────────────────

    @Command(name = "roundtrip",
            description = {"Round-trip test hs_err files: parse → toString, and optionally parse → JSON → fromJSON → toString."},
            mixinStandardHelpOptions = true)
    public static class RoundTripCmd implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to an hs_err file or directory to scan recursively")
        Path path;

        @Option(names = {"--json"}, description = "Also run JSON round-trip when text round-trip succeeds")
        boolean json;

        @Option(names = {"-w", "--watch"}, description = "Watch directory for new/changed hs_err files")
        boolean watch;

        @Override
        public Integer call() throws Exception {
            if (!Files.exists(path)) {
                System.err.println("Path does not exist: " + path);
                return 1;
            }

            int failures;
            if (Files.isDirectory(path)) {
                failures = testDirectory(path);
                if (watch) {
                    watchDirectory(path);
                    return 0; // unreachable, watchDirectory loops forever
                }
            } else {
                failures = testFile(path) == 1 ? 1 : 0;
            }
            return failures > 0 ? 1 : 0;
        }

        private int testDirectory(Path dir) throws IOException {
            int total = 0, passed = 0, failed = 0;
            try (Stream<Path> walk = Files.walk(dir)) {
                List<Path> files = walk
                        .filter(Files::isRegularFile)
                        .filter(RoundTripCmd::isHsErrFile)
                        .sorted()
                        .toList();
                for (Path file : files) {
                    int result = testFile(file);
                    if (result == 2) {
                        // SAP JVM files are skipped and not counted in total
                    } else {
                        total++;
                        if (result == 0) passed++;
                        else failed++;
                    }
                }
            }
            System.err.println();
            System.err.printf("Results: %d total, %d passed, %d failed", total, passed, failed);
            System.err.println();
            return failed;
        }

        /** @return 0 = pass, 1 = fail, 2 = skipped (silently) */
        private int testFile(Path file) {
            String name = file.getFileName().toString();
            try {
                String original;
                try {
                    original = Files.readString(file);
                } catch (java.nio.charset.MalformedInputException e) {
                    // Fall back to ISO-8859-1 for non-UTF-8 files
                    original = Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                if (isSapJvm(original)) {
                    // Silently skip SAP JVM files - no output
                    return 2;
                }
                // Normalize CRLF to LF for consistent comparison
                original = original.replace("\r\n", "\n");
                HsErrReport report = HsErrParser.parse(original);
                String printed = report.toString();

                List<String> diffs = compareLines(original, printed);
                if (!diffs.isEmpty()) {
                    System.err.printf("FAIL  %s  text round-trip (%d diffs)%n", name, diffs.size());
                    for (String d : diffs) System.err.println("  " + d);
                    return 1;
                }
                System.err.printf("OK    %s  text", name);

                if (json) {
                    String jsonStr = HsErrJson.toJson(report);
                    HsErrReport fromJson = HsErrJson.fromJson(jsonStr);
                    String jsonPrinted = fromJson.toString();
                    List<String> jsonDiffs = compareLines(original, jsonPrinted);
                    if (!jsonDiffs.isEmpty()) {
                        System.err.printf(" | FAIL json round-trip (%d diffs)%n", jsonDiffs.size());
                        for (String d : jsonDiffs) System.err.println("  " + d);
                        return 1;
                    }
                    System.err.print(" + json");
                }
                System.err.println();
                return 0;
            } catch (Exception e) {
                System.err.printf("FAIL  %s  %s: %s%n", name, e.getClass().getSimpleName(), e.getMessage());
                return 1;
            }
        }

        private void watchDirectory(Path dir) throws IOException, InterruptedException {
            System.err.println("Watching " + dir + " for hs_err files (Ctrl+C to stop)...");
            try (WatchService watcher = dir.getFileSystem().newWatchService()) {
                // Register all subdirectories
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(Files::isDirectory).forEach(d -> {
                        try {
                            d.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                        } catch (IOException e) {
                            System.err.println("Cannot watch: " + d + " (" + e.getMessage() + ")");
                        }
                    });
                }
                while (true) {
                    WatchKey key = watcher.take();
                    Path watchedDir = (Path) key.watchable();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path changed = watchedDir.resolve(ev.context());
                        if (Files.isRegularFile(changed) && isHsErrFile(changed)) {
                            // Small delay to let the file be fully written
                            Thread.sleep(200);
                            System.err.println();
                            testFile(changed);
                        } else if (Files.isDirectory(changed)) {
                            try {
                                changed.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                            } catch (IOException ignored) {}
                        }
                    }
                    key.reset();
                }
            }
        }

        static boolean isHsErrFile(Path p) {
            String name = p.getFileName().toString();
            return name.startsWith("hs_err") || name.startsWith("hserr");
        }

        /** SAP JVM hs_err files have a distinctive header line */
        private static boolean isSapJvm(String content) {
            // Check only the first few lines for the SAP marker
            int end = Math.min(content.length(), 200);
            return content.substring(0, end).contains("SAP Java Virtual Machine");
        }

        private static List<String> compareLines(String original, String printed) {
            String[] origLines = original.split("\n", -1);
            String[] printLines = printed.split("\n", -1);
            int minLen = Math.min(origLines.length, printLines.length);
            List<String> diffs = new ArrayList<>();
            for (int i = 0; i < minLen; i++) {
                if (!origLines[i].equals(printLines[i])) {
                    diffs.add(String.format("L%d: ORIG=[%s] GOT=[%s]", i + 1, origLines[i], printLines[i]));
                    if (diffs.size() >= 10) { diffs.add("...more"); break; }
                }
            }
            if (origLines.length != printLines.length) {
                diffs.add(String.format("Line count: orig=%d got=%d", origLines.length, printLines.length));
            }
            return diffs;
        }
    }

    public static void main(String[] args) {
        System.exit(FemtoCli.run(new HsErrTool(), args));
    }
}
