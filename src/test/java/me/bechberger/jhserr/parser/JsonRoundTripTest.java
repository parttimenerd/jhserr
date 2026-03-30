package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.HsErrJson;
import me.bechberger.jhserr.HsErrReport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies the JSON round-trip: hserr file → parse → JSON → fromJson → toString()
 * produces exactly the original file content for every sample hs_err file.
 */
class JsonRoundTripTest {

    @TestFactory
    Collection<DynamicTest> jsonRoundTripAllSamples() throws Exception {
        Path resourceDir = Path.of(JsonRoundTripTest.class.getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(JsonRoundTripTest::isSample).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("json-round-trip " + file.getFileName(), () -> {
                    String original = Files.readString(file);
                    HsErrReport report = HsErrParser.parse(file);

                    // Serialize to JSON and back
                    String json = HsErrJson.toJson(report);
                    HsErrReport fromJson = HsErrJson.fromJson(json);
                    String printed = fromJson.toString();

                    compareLineByLine(file.getFileName().toString(), original, printed);
                }));
            });
        }

        assertThat(tests).hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> jsonFileRoundTripAllSamples() throws Exception {
        Path resourceDir = Path.of(JsonRoundTripTest.class.getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(JsonRoundTripTest::isSample).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("json-file-round-trip " + file.getFileName(), () -> {
                    String original = Files.readString(file);
                    HsErrReport report = HsErrParser.parse(file);

                    // Write to temp JSON file, read back
                    Path tmpJson = Files.createTempFile("hserr-", ".json");
                    try {
                        HsErrJson.toJsonFile(report, tmpJson);
                        HsErrReport fromFile = HsErrJson.fromJsonFile(tmpJson);
                        String printed = fromFile.toString();

                        compareLineByLine(file.getFileName().toString(), original, printed);
                    } finally {
                        Files.deleteIfExists(tmpJson);
                    }
                }));
            });
        }

        assertThat(tests).hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> doubleJsonRoundTripAllSamples() throws Exception {
        Path resourceDir = Path.of(JsonRoundTripTest.class.getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(JsonRoundTripTest::isSample).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("double-json-round-trip " + file.getFileName(), () -> {
                    HsErrReport report = HsErrParser.parse(file);

                    // JSON → model → JSON should produce identical JSON
                    String json1 = HsErrJson.toJson(report);
                    HsErrReport fromJson = HsErrJson.fromJson(json1);
                    String json2 = HsErrJson.toJson(fromJson);

                    assertThat(json2).isEqualTo(json1);
                }));
            });
        }

        assertThat(tests).hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }

    private static boolean isSample(Path p) {
        String name = p.getFileName().toString();
        return Files.isRegularFile(p) && (name.startsWith("hs_err") || name.startsWith("hserr"));
    }

    private static void compareLineByLine(String fileName, String original, String printed) {
        String[] origLines = original.split("\n", -1);
        String[] printLines = printed.split("\n", -1);

        int minLen = Math.min(origLines.length, printLines.length);
        List<String> diffs = new ArrayList<>();
        for (int i = 0; i < minLen; i++) {
            if (!origLines[i].equals(printLines[i])) {
                diffs.add(String.format("Line %d:\n  ORIG:  [%s]\n  PRINT: [%s]",
                        i + 1, origLines[i], printLines[i]));
                if (diffs.size() >= 10) { diffs.add("...more"); break; }
            }
        }
        if (origLines.length != printLines.length) {
            diffs.add(String.format("Line count: orig=%d print=%d",
                    origLines.length, printLines.length));
        }
        if (!diffs.isEmpty()) {
            fail("%s has %d diffs:\n%s", fileName, diffs.size(), String.join("\n", diffs));
        }
    }
}
