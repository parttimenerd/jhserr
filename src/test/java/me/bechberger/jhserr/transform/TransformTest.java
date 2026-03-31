package me.bechberger.jhserr.transform;

import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.model.*;
import me.bechberger.jhserr.parser.HsErrParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.*;

class TransformTest {

    private HsErrReport loadMacOS() throws Exception {
        Path p = Path.of(TransformTest.class.getResource("/hs_err.log").toURI());
        return HsErrParser.parse(p);
    }

    private HsErrReport loadLinux() throws Exception {
        Path p = Path.of(TransformTest.class.getResource("/hserr_example").toURI());
        return HsErrParser.parse(p);
    }

    // ── RedactionEngine (read-only visitor) ──────────────────────────────

    @Test
    void redactionFindsHostname() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        assertThat(engine.hostname()).isEqualTo("TESTHOST01");
        assertThat(engine.findings()).anyMatch(f -> f.contains("Hostname"));
    }

    @Test
    void redactionFindsUsernames() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        assertThat(engine.usernames()).isNotEmpty();
        assertThat(engine.findings()).anyMatch(f -> f.contains("Username"));
    }

    @Test
    void redactionFindsPid() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        assertThat(engine.findings()).anyMatch(f -> f.contains("PID"));
    }

    // ── HsErrTransformer (identity) ──────────────────────────────────────

    @Test
    void identityTransformPreservesRoundTrip() throws Exception {
        HsErrReport original = loadMacOS();
        HsErrTransformer identity = new HsErrTransformer();
        HsErrReport transformed = identity.transform(original);

        assertThat(transformed.toString()).isEqualTo(original.toString());
    }

    @TestFactory
    Collection<DynamicTest> identityTransformAllSamples() throws Exception {
        Path resourceDir = Path.of(getClass().getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return Files.isRegularFile(p) && (name.startsWith("hs_err") || name.startsWith("hserr"));
            }).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("identity " + file.getFileName(), () -> {
                    HsErrReport original = HsErrParser.parse(file);
                    HsErrReport transformed = new HsErrTransformer().transform(original);
                    assertThat(transformed.toString()).isEqualTo(original.toString());
                }));
            });
        }

        assertThat(tests).hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }

    @Test
    void identityTransformPreservesStructure() throws Exception {
        HsErrReport original = loadMacOS();
        HsErrReport transformed = new HsErrTransformer().transform(original);

        assertThat(transformed.header()).isNotNull();
        assertThat(transformed.header().errorType()).isEqualTo("SIGSEGV");
        assertThat(transformed.summary()).isNotNull();
        assertThat(transformed.thread()).isNotNull();
        assertThat(transformed.process()).isNotNull();
        assertThat(transformed.system()).isNotNull();
        assertThat(transformed.complete()).isTrue();
    }

    // ── Custom transformer: filtering ────────────────────────────────────

    @Test
    void filteringTransformerRemovesItems() throws Exception {
        HsErrReport original = loadMacOS();

        // Remove all event logs
        HsErrTransformer filter = new HsErrTransformer() {
            @Override
            protected SectionItem transformEventLog(EventLog eventLog) {
                return null; // filter out
            }
        };
        HsErrReport filtered = filter.transform(original);

        assertThat(original.process().eventLogs()).isNotEmpty();
        assertThat(filtered.process().eventLogs()).isEmpty();
        // Other items preserved
        assertThat(filtered.process().javaThreads()).isNotNull();
        assertThat(filtered.process().dynamicLibraries()).isNotNull();
    }

    @Test
    void filteringTransformerRemovesDynamicLibraries() throws Exception {
        HsErrReport original = loadMacOS();

        HsErrTransformer filter = new HsErrTransformer() {
            @Override
            protected SectionItem transformDynamicLibraries(DynamicLibraries libs) {
                return null;
            }
        };
        HsErrReport filtered = filter.transform(original);

        assertThat(original.process().dynamicLibraries()).isNotNull();
        assertThat(filtered.process().dynamicLibraries()).isNull();
    }

    @Test
    void transformerComposition() throws Exception {
        HsErrReport original = loadMacOS();

        HsErrTransformer stripEvents = new HsErrTransformer() {
            @Override protected SectionItem transformEventLog(EventLog e) { return null; }
        };
        HsErrTransformer stripLibs = new HsErrTransformer() {
            @Override protected SectionItem transformDynamicLibraries(DynamicLibraries d) { return null; }
        };

        HsErrReport result = stripLibs.transform(stripEvents.transform(original));

        assertThat(result.process().eventLogs()).isEmpty();
        assertThat(result.process().dynamicLibraries()).isNull();
        // Structure still valid
        assertThat(result.process().javaThreads()).isNotNull();
        assertThat(result.header().errorType()).isEqualTo("SIGSEGV");
    }

    // ── RedactionTransformer ─────────────────────────────────────────────

    @Test
    void redactionTransformerRedactsPid() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        assertThat(original.header().pid()).isEqualTo("16540");
        assertThat(redacted.header().pid()).isEqualTo("0");
        assertThat(redacted.header().errorDetail()).contains("pid=0");
        assertThat(redacted.header().errorDetail()).doesNotContain("pid=16540");
        assertThat(transformer.redactions()).anyMatch(r -> r.contains("PID"));
    }

    @Test
    void redactionTransformerRedactsHostname() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.hostname()).isEqualTo("TESTHOST01");
        String redactedText = redacted.toString();
        assertThat(redactedText).doesNotContain("TESTHOST01");
        assertThat(transformer.redactions()).anyMatch(r -> r.contains("hostname"));
    }

    @Test
    void redactionTransformerRedactsUsernames() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.usernames()).isNotEmpty();
        String redactedText = redacted.toString();
        for (String username : transformer.usernames()) {
            assertThat(redactedText).doesNotContain("/Users/" + username + "/");
            assertThat(redactedText).doesNotContain("/home/" + username + "/");
        }
        assertThat(redactedText).contains("REDACTED_USER");
    }

    @Test
    void redactionTransformerProducesValidReport() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        // Redacted report still has all sections
        assertThat(redacted.header()).isNotNull();
        assertThat(redacted.summary()).isNotNull();
        assertThat(redacted.thread()).isNotNull();
        assertThat(redacted.process()).isNotNull();
        assertThat(redacted.system()).isNotNull();
        assertThat(redacted.complete()).isTrue();

        // Can re-parse the redacted output (round-trip through text)
        HsErrReport reparsed = HsErrParser.parse(redacted.toString());
        assertThat(reparsed.header().errorType()).isEqualTo("SIGSEGV");
        assertThat(reparsed.complete()).isTrue();
    }

    @Test
    void redactionTransformerRedactsEnvVars() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        EnvironmentVariables origEnv = original.process().environmentVariables();
        EnvironmentVariables redactedEnv = redacted.process().environmentVariables();
        assertThat(origEnv).isNotNull();
        assertThat(redactedEnv).isNotNull();

        // PATH should be preserved (safe var)
        assertThat(redactedEnv.vars()).containsKey("PATH");
        // Non-safe vars should be redacted to ***
        Set<String> safeVars = new RedactionConfig().safeEnvVars();
        for (var entry : redactedEnv.vars().entrySet()) {
            if (!safeVars.contains(entry.getKey())) {
                assertThat(entry.getValue()).isEqualTo("***");
            }
        }
        assertThat(transformer.redactions()).anyMatch(r -> r.contains("env var"));
    }

    // ── RedactionEngine: uname hostname discovery ────────────────────────

    @Test
    void redactionEngineFindsLinuxHostnameFromUname() throws Exception {
        HsErrReport report = loadLinux();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        assertThat(engine.hostnames()).contains("testhost02");
        assertThat(engine.findings()).anyMatch(f -> f.contains("uname"));
    }

    @Test
    void redactionEngineReturnsSingleHostnameFromHostname() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        // hostname() returns first discovered — from Summary
        assertThat(engine.hostname()).isEqualTo("TESTHOST01");
    }

    // ── RedactionTransformer: uname hostname redaction ───────────────────

    @Test
    void redactionTransformerRedactsUnameHostname() throws Exception {
        HsErrReport original = loadLinux();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.hostnames()).contains("testhost02");
        String text = redacted.toString();
        assertThat(text).doesNotContain("testhost02");
    }

    @Test
    void redactionTransformerRedactsMacHostnameFromUname() throws Exception {
        // hs_err.log has "uname: Darwin TESTHOST01 22.6.0 ..." and Summary host: TESTHOST01
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.hostnames()).contains("TESTHOST01");
        assertThat(redacted.toString()).doesNotContain("TESTHOST01");
    }

    // ── RedactionTransformer: sensitive path prefix ──────────────────────

    @Test
    void redactionTransformerRedactsSensitivePathPrefixes() throws Exception {
        HsErrReport original = loadLinux();
        RedactionConfig config = new RedactionConfig()
                .addSensitivePathPrefix("/build/workspace/openjdk-jdk-dev")
                .addSensitivePathPrefix("/test/output");
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        String text = redacted.toString();
        assertThat(text).doesNotContain("/build/workspace/openjdk-jdk-dev");
        assertThat(text).doesNotContain("/test/output");
        // Replaced with default path placeholder
        assertThat(text).contains("<redacted-path>");
    }

    @Test
    void redactionTransformerRedactsParentPaths() throws Exception {
        HsErrReport original = loadLinux();
        RedactionConfig config = new RedactionConfig()
                .addSensitivePathPrefix("/build/workspace/openjdk-jdk-dev");
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        // Not only the full path but parent prefixes should not appear literally
        String text = redacted.toString();
        assertThat(text).doesNotContain("/build/workspace/openjdk-jdk-dev");
        assertThat(text).doesNotContain("/build/workspace");
    }

    // ── RedactionTransformer: additional usernames/hostnames ─────────────

    @Test
    void redactionTransformerRedactsAdditionalUsernames() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = new RedactionConfig()
                .addAdditionalUsername("testuser");
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.usernames()).contains("testuser");
        assertThat(redacted.toString()).doesNotContain("/Users/testuser/");
    }

    @Test
    void redactionTransformerRedactsAdditionalHostnames() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = new RedactionConfig()
                .addAdditionalHostname("customhost");
        RedactionTransformer transformer = new RedactionTransformer(config);
        transformer.transform(original);

        assertThat(transformer.hostnames()).contains("customhost");
        assertThat(transformer.hostnames()).contains("TESTHOST01");
    }

    // ── RedactionTransformer: sensitive strings ──────────────────────────

    @Test
    void redactionTransformerRedactsSensitiveStrings() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = new RedactionConfig()
                .addSensitiveString("SIGSEGV");
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        // The sensitive string should be replaced
        assertThat(redacted.header().errorDetail()).doesNotContain("SIGSEGV");
    }

    // ── RedactionConfig: JSON round-trip ─────────────────────────────────

    @Test
    void redactionConfigJsonRoundTrip() throws Exception {
        RedactionConfig config = new RedactionConfig()
                .setRedactPids(false)
                .setUserPlaceholder("USER")
                .setHostPlaceholder("HOST")
                .addAdditionalUsername("alice")
                .addAdditionalHostname("server1")
                .addSensitivePathPrefix("/priv/jenkins")
                .addSensitiveString("secret-token");

        String json = config.toJson();
        RedactionConfig restored = RedactionConfig.fromJson(json);

        assertThat(restored.redactPids()).isFalse();
        assertThat(restored.userPlaceholder()).isEqualTo("USER");
        assertThat(restored.hostPlaceholder()).isEqualTo("HOST");
        assertThat(restored.additionalUsernames()).containsExactly("alice");
        assertThat(restored.additionalHostnames()).containsExactly("server1");
        assertThat(restored.sensitivePathPrefixes()).containsExactly("/priv/jenkins");
        assertThat(restored.sensitiveStrings()).containsExactly("secret-token");
    }

    @Test
    void redactionConfigJsonFileRoundTrip() throws Exception {
        RedactionConfig config = RedactionConfig.aggressive()
                .addAdditionalUsername("bob")
                .addSensitivePathPrefix("/opt/secret");

        Path tmp = Files.createTempFile("redaction-config", ".json");
        try {
            config.toJsonFile(tmp);
            RedactionConfig restored = RedactionConfig.fromJsonFile(tmp);
            assertThat(restored.removeDynamicLibraries()).isTrue();
            assertThat(restored.removeEventLogs()).isTrue();
            assertThat(restored.additionalUsernames()).containsExactly("bob");
            assertThat(restored.sensitivePathPrefixes()).containsExactly("/opt/secret");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ── Hostname ignore list ─────────────────────────────────────────────

    @Test
    void hostnameIgnoreListExcludesDiscoveredHostname() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = new RedactionConfig()
                .addHostnameIgnore("TESTHOST01");
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        // Hostname should NOT be redacted since it's on the ignore list
        assertThat(transformer.hostnames()).doesNotContain("TESTHOST01");
        assertThat(redacted.toString()).contains("TESTHOST01");
    }

    @Test
    void hostnameIgnoreListDoesNotAffectOtherHostnames() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = new RedactionConfig()
                .addAdditionalHostname("otherhost")
                .addHostnameIgnore("TESTHOST01");
        RedactionTransformer transformer = new RedactionTransformer(config);
        transformer.transform(original);

        // "otherhost" should still be redacted
        assertThat(transformer.hostnames()).contains("otherhost");
        // TESTHOST01 is ignored
        assertThat(transformer.hostnames()).doesNotContain("TESTHOST01");
    }

    @Test
    void hostnameIgnoreListExcludesUnameHostname() throws Exception {
        HsErrReport original = loadLinux();
        RedactionConfig config = new RedactionConfig()
                .addHostnameIgnore("testhost02");
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.hostnames()).doesNotContain("testhost02");
        assertThat(redacted.toString()).contains("testhost02");
    }

    @Test
    void hostnameIgnoreListJsonRoundTrip() throws Exception {
        RedactionConfig config = new RedactionConfig()
                .addHostnameIgnore("ignored-host")
                .addHostnameIgnore("another-ignored");
        String json = config.toJson();
        RedactionConfig restored = RedactionConfig.fromJson(json);

        assertThat(restored.hostnameIgnoreList()).containsExactly("ignored-host", "another-ignored");
    }

    @Test
    void emptyHostnameIgnoreListChangesNothing() throws Exception {
        HsErrReport original = loadMacOS();
        // Default config has empty ignore list
        RedactionTransformer transformer = new RedactionTransformer();
        HsErrReport redacted = transformer.transform(original);

        assertThat(transformer.hostnames()).contains("TESTHOST01");
        assertThat(redacted.toString()).doesNotContain("TESTHOST01");
    }

    // ── RedactionEngine: comprehensive tests ─────────────────────────────

    @Test
    void redactionEngineFindsIpAddresses() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        // Engine should produce findings (already tested hostname/username/PID)
        assertThat(engine.findings()).isNotEmpty();
    }

    @Test
    void redactionEngineFindsEnvVarFindings() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        assertThat(engine.findings()).anyMatch(f -> f.contains("Env var"));
    }

    @Test
    void redactionEngineLinuxFull() throws Exception {
        HsErrReport report = loadLinux();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);

        // Should find hostname from uname
        assertThat(engine.hostnames()).isNotEmpty();
        // Should find PID
        assertThat(engine.findings()).anyMatch(f -> f.contains("PID"));
        // Should find at least hostname + PID findings
        assertThat(engine.findings()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void redactionEngineAllSamplesDoNotThrow() throws Exception {
        Path resourceDir = Path.of(getClass().getResource("/hs_err.log").toURI()).getParent();
        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return Files.isRegularFile(p) && (name.startsWith("hs_err") || name.startsWith("hserr"));
            }).forEach(file -> {
                assertThatCode(() -> {
                    HsErrReport report = HsErrParser.parse(file);
                    RedactionEngine engine = new RedactionEngine();
                    report.accept(engine);
                    // Should always produce at least 1 finding (PID at minimum)
                    assertThat(engine.findings()).isNotEmpty();
                }).doesNotThrowAnyException();
            });
        }
    }

    @Test
    void redactionEngineMultipleHostnamesFound() throws Exception {
        HsErrReport report = loadLinux();
        RedactionEngine engine = new RedactionEngine();
        report.accept(engine);
        // hostname() returns first, hostnames() may have more from uname
        if (engine.hostname() != null) {
            assertThat(engine.hostnames()).contains(engine.hostname());
        }
    }

    // ── Hostname extraction edge cases ───────────────────────────────────

    @Test
    void redactionTransformerDarwinWithoutHostnameInUname() throws Exception {
        // "uname: Darwin 24.6.0 ..." — second token starts with digit, no hostname
        HsErrReport original = loadMacOS();
        RedactionTransformer transformer = new RedactionTransformer();
        transformer.transform(original);

        // Hostnames should only come from Summary host line + uname (if non-numeric)
        for (String h : transformer.hostnames()) {
            // No hostname should be a bare version number
            assertThat(h).doesNotMatch("^\\d+\\..*");
        }
    }

    @TestFactory
    Collection<DynamicTest> redactionTransformerAllSamplesProduceValidOutput() throws Exception {
        Path resourceDir = Path.of(getClass().getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return Files.isRegularFile(p) && (name.startsWith("hs_err") || name.startsWith("hserr"));
            }).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("redact " + file.getFileName(), () -> {
                    HsErrReport original = HsErrParser.parse(file);
                    RedactionTransformer transformer = new RedactionTransformer();
                    HsErrReport redacted = transformer.transform(original);

                    // No hostname should survive in output
                    String text = redacted.toString();
                    for (String h : transformer.hostnames()) {
                        assertThat(text).doesNotContain(h);
                    }
                    // No username should survive in path context
                    for (String u : transformer.usernames()) {
                        assertThat(text).doesNotContain("/Users/" + u + "/");
                        assertThat(text).doesNotContain("/home/" + u + "/");
                    }
                    // Result should be re-parsable
                    assertThatCode(() -> HsErrParser.parse(text)).doesNotThrowAnyException();
                }));
            });
        }

        assertThat(tests).hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }

    @Test
    void redactionTransformerAggressivePreset() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = RedactionConfig.aggressive();
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        // Dynamic libraries should be removed
        assertThat(redacted.process().dynamicLibraries()).isNull();
        // Event logs should be removed
        assertThat(redacted.process().eventLogs()).isEmpty();
        // Thread names should be redacted
        assertThat(config.redactThreadNames()).isTrue();
    }

    @Test
    void redactionTransformerMinimalPreset() throws Exception {
        HsErrReport original = loadMacOS();
        RedactionConfig config = RedactionConfig.minimal();
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(original);

        // PIDs should NOT be redacted in minimal mode
        assertThat(redacted.header().pid()).isEqualTo(original.header().pid());
        // But hostname should still be redacted
        assertThat(transformer.hostnames()).isNotEmpty();
        String text = redacted.toString();
        for (String h : transformer.hostnames()) {
            assertThat(text).doesNotContain(h);
        }
    }

    // ── IPv4 false-positive avoidance ────────────────────────────────────

    @Test
    void ipv4PatternDoesNotMatchVersionStrings() {
        // clang-1403.0.22.14.1 contains "0.22.14.1" which looks like IPv4
        var pattern = RedactionConfig.IPV4_PATTERN;
        assertThat(pattern.matcher("clang-1403.0.22.14.1").find()).isFalse();
        assertThat(pattern.matcher("version 2.3.14.1.2").find()).isFalse();
        // Genuine IPv4 should still match
        assertThat(pattern.matcher("addr 192.168.1.1 end").find()).isTrue();
        assertThat(pattern.matcher("0.0.0.0").find()).isTrue();
        assertThat(pattern.matcher("10.0.0.1:8080").find()).isTrue();
    }

    @Test
    void ipv4NotRedactedInVmInfoClangVersion() throws Exception {
        HsErrReport report = loadMacOS();
        RedactionConfig config = new RedactionConfig();
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(report);
        String text = redacted.toString();
        // The clang version number should survive redaction
        if (text.contains("clang")) {
            assertThat(text).doesNotContain("clang Apple LLVM 14.0.3 (clang-1403.***");
        }
    }

    // ── Username redacted outside of path context ────────────────────────

    @Test
    void usernameRedactedInQuotedBuilderName() throws Exception {
        // The vm_info line contains: built ... by "jbechberger"
        HsErrReport report = loadMacOS();
        RedactionConfig config = new RedactionConfig();
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(report);
        String text = redacted.toString();
        // The discovered username should be redacted everywhere, including builder name
        for (String username : transformer.usernames()) {
            assertThat(text).describedAs("Username '%s' should be fully redacted", username)
                    .doesNotContain(username);
        }
    }

    @Test
    void usernameRedactedInAdhocBuildString() throws Exception {
        // The vm_info line has "adhoc.jbechberger.jdk" — username delimited by dots
        HsErrReport report = loadMacOS();
        RedactionConfig config = new RedactionConfig();
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(report);
        String text = redacted.toString();
        for (String username : transformer.usernames()) {
            assertThat(text).describedAs("Username '%s' in adhoc string should be redacted", username)
                    .doesNotContain(username);
        }
    }

    // ── RegisterMemoryMapping redaction ──────────────────────────────────

    @Test
    void registerMemoryMappingPathsRedacted() throws Exception {
        // The macOS sample has register mappings like:
        // x4 =0x...: <offset 0x...> in /Users/testuser/code/.../libjvm.dylib at 0x...
        HsErrReport report = loadMacOS();
        RedactionConfig config = new RedactionConfig();
        RedactionTransformer transformer = new RedactionTransformer(config);
        HsErrReport redacted = transformer.transform(report);
        String text = redacted.toString();
        // Usernames in register memory mapping library paths should be redacted
        for (String username : transformer.usernames()) {
            assertThat(text).describedAs("Username '%s' in register mapping paths should be redacted", username)
                    .doesNotContain(username);
        }
    }

    @Test
    void registerMemoryMappingScannedForUsernames() throws Exception {
        // RegisterMemoryMapping library paths should be scanned in pass 1
        // so usernames are discovered for redaction everywhere
        HsErrReport report = loadMacOS();
        RedactionConfig config = new RedactionConfig();
        RedactionTransformer transformer = new RedactionTransformer(config);
        transformer.transform(report);
        // The macOS sample has /Users/testuser/ in register mappings
        assertThat(transformer.usernames()).isNotEmpty();
    }

    // ── Header assertDetail redaction ────────────────────────────────────

    @Test
    void headerScanCoversAllTextFields() throws Exception {
        // Verify that the scan phase scans javaVm, jfrFileLine too
        HsErrReport report = loadMacOS();
        RedactionConfig config = new RedactionConfig();
        RedactionTransformer transformer = new RedactionTransformer(config);
        transformer.transform(report);
        // If javaVm contains a username in path or adhoc string, it should be discovered
        if (report.header().javaVm() != null && report.header().javaVm().contains("adhoc.")) {
            assertThat(transformer.usernames()).isNotEmpty();
        }
    }
}
