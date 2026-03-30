package me.bechberger.jhserr.parser;

import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.*;

/**
 * Temporary test to dump all section items for analysis.
 */
class DumpItemsTest {

    @Test
    void dumpAllItems() throws Exception {
        String[] files = {"hs_err.log", "hserr_example", "hs_err_pid42500.log"};
        for (String f : files) {
            var url = getClass().getResource("/" + f);
            if (url == null) continue;
            HsErrReport report = HsErrParser.parse(Path.of(url.toURI()));
            System.out.println("=== " + f + " ===");

            if (report.thread() != null) {
                System.out.println("\n-- THREAD --");
                for (int i = 0; i < report.thread().items().size(); i++) {
                    SectionItem item = report.thread().items().get(i);
                    System.out.printf("[%d] %s: %s%n", i, item.getClass().getSimpleName(), describe(item));
                }
            }
            if (report.process() != null) {
                System.out.println("\n-- PROCESS --");
                for (int i = 0; i < report.process().items().size(); i++) {
                    SectionItem item = report.process().items().get(i);
                    System.out.printf("[%d] %s: %s%n", i, item.getClass().getSimpleName(), describe(item));
                }
            }
            if (report.system() != null) {
                System.out.println("\n-- SYSTEM --");
                for (int i = 0; i < report.system().items().size(); i++) {
                    SectionItem item = report.system().items().get(i);
                    System.out.printf("[%d] %s: %s%n", i, item.getClass().getSimpleName(), describe(item));
                }
            }
            System.out.println();
        }
    }

    static String describe(SectionItem item) {
        if (item instanceof BlankLine) return "<blank>";
        if (item instanceof VmInfo vi) return "vm_info: " + vi.line().substring(0, Math.min(vi.line().length(), 60)) + "...";
        if (item instanceof NamedSection ns) return ns.name() + " (+" + ns.lines().size() + " body lines)";
        return item.getClass().getSimpleName();
    }
}
