package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.model.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Parses every sample hs_err file in test resources to ensure the parser
 * handles all variants without exceptions and produces a valid model.
 */
class AllSamplesTest {

    @TestFactory
    Collection<DynamicTest> parseAllSamples() throws Exception {
        Path resourceDir = Path.of(AllSamplesTest.class.getResource("/hs_err.log").toURI()).getParent();
        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> files = Files.list(resourceDir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return Files.isRegularFile(p) && (name.startsWith("hs_err") || name.startsWith("hserr"));
            }).sorted().forEach(file -> {
                tests.add(DynamicTest.dynamicTest("parse " + file.getFileName(), () -> {
                    HsErrReport report = HsErrParser.parse(file);

                    // Every report must have a header
                    assertThat(report.header()).isNotNull();
                    assertThat(report.header().errorDetail()).isNotNull();

                    // Summary should be present
                    assertThat(report.summary()).isNotNull();

                    // Thread section should be present
                    assertThat(report.thread()).isNotNull();

                    // Process section should be present
                    assertThat(report.process()).isNotNull();

                    // System section should be present
                    assertThat(report.system()).isNotNull();

                    // toString should not throw
                    String text = report.toString();
                    assertThat(text).isNotEmpty();
                }));
            });
        }

        assertThat(tests).as("Expected at least 2 test resource files").hasSizeGreaterThanOrEqualTo(2);
        return tests;
    }
}
