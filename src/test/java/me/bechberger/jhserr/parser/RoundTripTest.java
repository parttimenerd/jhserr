package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.HsErrReport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies that parse → toString() produces exactly the original file content
 * for every sample hs_err file.
 */
class RoundTripTest {

    @TestFactory
    Collection<DynamicTest> roundTripAllSamples() throws Exception {
        Path resourceDir = Path.of(RoundTripTest.class.getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return Files.isRegularFile(p) && (name.startsWith("hs_err") || name.startsWith("hserr"));
            }).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("round-trip " + file.getFileName(), () -> {
                    String original = Files.readString(file);
                    HsErrReport report = HsErrParser.parse(file);
                    String printed = report.toString();
                    
                    // Compare line by line for helpful diffs
                    String[] origLines = original.split("\n", -1);
                    String[] printLines = printed.split("\n", -1);
                    
                    // Collect ALL diffs for a comprehensive message
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
                        fail("%s has %d diffs:\n%s", file.getFileName(),
                             diffs.size(), String.join("\n", diffs));
                    }
                }));
            });
        }

        assertThat(tests).hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }
}
