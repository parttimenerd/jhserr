package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import me.bechberger.jhserr.HsErrVisitor;
import me.bechberger.jhserr.model.location.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.*;

/**
 * Parsed "Register to memory mapping:" subsection from THREAD section.
 *
 * <p>Each register+address is parsed into a typed {@link RegisterMemoryMapping.Entry}
 * with the location interpretation (library, stack, oop, heap, malloc, mmap, unknown).
 *
 * <p>Example input:
 * <pre>
 * Register to memory mapping:
 *
 * pc =0x00007fff92c1a4f0: <offset 0x0000000001c1a4f0> in /path/to/libjvm.so at 0x00007fff91000000
 * lr =0x00007fff92c1a4a0: <offset 0x0000000001c1a4a0> in /path/to/libjvm.so at 0x00007fff91000000
 * r1 =0x00007fff60ffccf0 is pointing into the stack for thread: 0x00007fff8c481fc0
 * r17=0x00000000fd580000 is an oop: gc.stress.gcold.TreeNode
 * ...
 * </pre>
 */
@JsonTypeName("RegisterMemoryMapping")
public final class RegisterMemoryMapping implements ThreadSectionItem {
    private final @NotNull List<Entry> entries;

    /**
     * Single register→location mapping entry.
     */
    public record Entry(
            @JsonProperty("registerName") @NotNull String registerName,
            @JsonProperty("address") @NotNull String address,
            @JsonProperty("location") @NotNull RegisterLocation location
    ) {
        /**
         * Reconstruct the original line from parsed fields.
         */
        public String toString() {
            if (location instanceof LibraryLocation lib) {
                return registerName + " =" + address + ": <offset " + lib.offset() + "> in " + lib.libraryPath() + " at " + lib.baseAddress();
            } else if (location instanceof StackLocation stack) {
                return registerName + " =" + address + " is pointing into the stack for thread: " + stack.threadPtr();
            } else if (location instanceof OopLocation oop) {
                return registerName + " =" + address + " is an oop: " + oop.className() + (oop.details() != null ? " " + oop.details() : "");
            } else if (location instanceof HeapLocation heap) {
                return registerName + " =" + address + " is " + heap.description() + " location in the heap";
            } else if (location instanceof MallocLocation malloc) {
                return registerName + " =" + address + " into live malloced block starting at " + malloc.blockStart() + ", size " + malloc.size() + ", tag " + malloc.tag();
            } else if (location instanceof MmapLocation mmap) {
                return registerName + " =" + address + " in mmap'd memory region [" + mmap.regionStart() + " - " + mmap.regionEnd() + "], tag " + mmap.tag();
            } else {
                return registerName + " =" + address + ": " + location;
            }
        }
    }

    @JsonCreator
    public RegisterMemoryMapping(@JsonProperty("entries") @NotNull List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * Create from raw lines (parser's responsibility).
     *
     * @param lines body lines from "Register to memory mapping:" section (excluding header, including blanks)
     * @return parsed RegisterMemoryMapping
     */
    public static @NotNull RegisterMemoryMapping fromLines(@NotNull List<String> lines) {
        List<Entry> entries = new ArrayList<>();
        for (String line : lines) {
            line = line.strip();
            if (line.isBlank()) continue;
            Entry entry = parseLine(line);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new RegisterMemoryMapping(entries);
    }

    private static final Pattern REGISTER_PATTERN = Pattern.compile("^([a-z0-9]+)\\s*=(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIBRARY_PATTERN = Pattern.compile("^(0x[0-9a-f]+):\\s*<offset\\s+(0x[0-9a-f]+)>\\s+in\\s+(.+?)\\s+at\\s+(0x[0-9a-f]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STACK_PATTERN = Pattern.compile("^(0x[0-9a-f]+)\\s+is pointing into the stack for thread:\\s+(0x[0-9a-f]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OOP_PATTERN = Pattern.compile("^(0x[0-9a-f]+)\\s+is an oop:\\s+([\\w.$]+)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAP_PATTERN = Pattern.compile("^(0x[0-9a-f]+)\\s+is (\\w+(?:\\s+\\w+)*)\\s+(?:location in the heap|in the heap)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MALLOC_PATTERN = Pattern.compile("^(0x[0-9a-f]+)\\s+into live malloced block starting at\\s+(0x[0-9a-f]+),\\s+size\\s+([0-9]+),\\s+tag\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MMAP_PATTERN = Pattern.compile("^(0x[0-9a-f]+)\\s+in mmap'd memory region\\s+\\[(0x[0-9a-f]+)\\s*-\\s*(0x[0-9a-f]+)\\],?\\s+tag\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);

    private static Entry parseLine(String line) {
        Matcher m = REGISTER_PATTERN.matcher(line);
        if (!m.find()) return null;

        String regName = m.group(1);
        String rest = m.group(2);

        // Try each location pattern
        Matcher libMatch = LIBRARY_PATTERN.matcher(rest);
        if (libMatch.find()) {
            return new Entry(
                    regName,
                    libMatch.group(1),
                    new LibraryLocation(libMatch.group(1), libMatch.group(2), libMatch.group(3), libMatch.group(4))
            );
        }

        Matcher stackMatch = STACK_PATTERN.matcher(rest);
        if (stackMatch.find()) {
            return new Entry(
                    regName,
                    stackMatch.group(1),
                    new StackLocation(stackMatch.group(1), stackMatch.group(2))
            );
        }

        Matcher oopMatch = OOP_PATTERN.matcher(rest);
        if (oopMatch.find()) {
            String details = oopMatch.group(3).strip();
            return new Entry(
                    regName,
                    oopMatch.group(1),
                    new OopLocation(oopMatch.group(1), oopMatch.group(2), details.isEmpty() ? null : details)
            );
        }

        Matcher heapMatch = HEAP_PATTERN.matcher(rest);
        if (heapMatch.find()) {
            return new Entry(
                    regName,
                    heapMatch.group(1),
                    new HeapLocation(heapMatch.group(1), heapMatch.group(2))
            );
        }

        Matcher mallocMatch = MALLOC_PATTERN.matcher(rest);
        if (mallocMatch.find()) {
            return new Entry(
                    regName,
                    mallocMatch.group(1),
                    new MallocLocation(mallocMatch.group(1), mallocMatch.group(2), mallocMatch.group(3), mallocMatch.group(4))
            );
        }

        Matcher mmapMatch = MMAP_PATTERN.matcher(rest);
        if (mmapMatch.find()) {
            return new Entry(
                    regName,
                    mmapMatch.group(1),
                    new MmapLocation(mmapMatch.group(1), mmapMatch.group(2), mmapMatch.group(3), mmapMatch.group(4))
            );
        }

        // Fallback: treat as unknown
        return new Entry(
                regName,
                extractAddress(rest),
                new UnknownRegisterLocation(rest)
        );
    }

    private static String extractAddress(String text) {
        Matcher m = Pattern.compile("^(0x[0-9a-f]+)").matcher(text);
        return m.find() ? m.group(1) : "unknown";
    }

    public @NotNull List<Entry> entries() {
        return entries;
    }

    @Override
    public void accept(HsErrVisitor v) {
        v.visitRegisterMemoryMapping(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Register to memory mapping:\n\n");
        for (Entry entry : entries) {
            sb.append(entry.toString()).append("\n");
        }
        return sb.toString();
    }
}
