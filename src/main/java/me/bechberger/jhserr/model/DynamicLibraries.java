package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.*;

/**
 * Dynamic library entries from the PROCESS section.
 *
 * <p>Each entry is split into a metadata prefix and an optional path,
 * enabling targeted redaction of file paths.
 */
public final class DynamicLibraries implements ProcessSectionItem {

    private final @NotNull String header;
    private final @NotNull List<LibraryEntry> entries;

    /**
     * A single dynamic library entry, split into metadata prefix and optional path.
     *
     * <p>macOS: prefix is {@code "0xADDR\t"}, path is the library path.
     * <p>Linux: prefix is the /proc/self/maps metadata (address range, perms, offset, dev, inode + spacing),
     * path is the mapped file or pseudo-path like {@code [heap]}.
     */
    public record LibraryEntry(
            @JsonProperty("prefix") @NotNull String prefix,
            @JsonProperty("path") @Nullable String path) {

        @JsonIgnore
        public @NotNull String line() { return path != null ? prefix + path : prefix; }

        private static final Pattern LINUX_PATH = Pattern.compile("\\s(/.+|\\[.+])\\s*$");

        public static @NotNull LibraryEntry fromLine(@NotNull String line) {
            // macOS: tab-separated (address\tpath)
            int tab = line.indexOf('\t');
            if (tab >= 0 && tab < line.length() - 1) {
                return new LibraryEntry(line.substring(0, tab + 1), line.substring(tab + 1));
            }
            // Linux /proc/self/maps: path after whitespace, starting with / or [
            Matcher m = LINUX_PATH.matcher(line);
            if (m.find()) {
                return new LibraryEntry(line.substring(0, m.start(1)), m.group(1));
            }
            return new LibraryEntry(line, null);
        }
    }

    @JsonCreator
    public DynamicLibraries(@JsonProperty("header") @NotNull String header,
                            @JsonProperty("entries") @NotNull List<LibraryEntry> entries) {
        this.header = header;
        this.entries = List.copyOf(entries);
    }

    public static @NotNull DynamicLibraries fromLines(@NotNull String header, @NotNull List<String> lines) {
        return new DynamicLibraries(header, lines.stream().map(LibraryEntry::fromLine).toList());
    }

    @JsonProperty public @NotNull String header() { return header; }
    @JsonProperty public @NotNull List<LibraryEntry> entries() { return entries; }

    public void accept(HsErrVisitor v) { v.visitDynamicLibraries(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (LibraryEntry e : entries) sb.append(e.line()).append('\n');
        return sb.toString();
    }
}
