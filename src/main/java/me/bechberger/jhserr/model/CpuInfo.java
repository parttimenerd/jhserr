package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.*;

import me.bechberger.jhserr.HsErrVisitor;

/**
 * Parsed CPU: sub-section from the SYSTEM section.
 *
 * <p>Extends {@link NamedSection} for round-trip storage (header + body lines).
 * Well-known fields are parsed eagerly at construction time from the header line.
 *
 * <p>Header format: "CPU: total N (initial active M) FEATURES"
 *
 * <p>macOS body lines include hw.*cache* and machdep.cpu.brand_string.
 * Linux body includes /proc/cpuinfo content.
 */
public non-sealed class CpuInfo implements SystemSectionItem {

    private final @NotNull String name;
    private final @NotNull List<String> lines;

    // Eagerly parsed from header
    private final int totalCpus;
    private final int initialActiveCpus;
    private final @Nullable String features;

    // Eagerly parsed from body
    private final @Nullable String cpuBrand;

    private static final Pattern TOTAL_PAT = Pattern.compile("total\\s+(\\d+)");
    private static final Pattern ACTIVE_PAT = Pattern.compile("initial active\\s+(\\d+)");
    private static final Pattern BRAND_PAT = Pattern.compile("^machdep\\.cpu\\.brand_string:(.+)$");

    @JsonCreator
    public CpuInfo(@JsonProperty("name") @NotNull String name,
                   @JsonProperty("lines") @NotNull List<String> lines) {
        this.name = Objects.requireNonNull(name, "name");
        this.lines = lines != null ? List.copyOf(lines) : List.of();

        // Parse header fields
        Matcher m = TOTAL_PAT.matcher(name);
        this.totalCpus = m.find() ? Integer.parseInt(m.group(1)) : -1;
        m = ACTIVE_PAT.matcher(name);
        this.initialActiveCpus = m.find() ? Integer.parseInt(m.group(1)) : -1;
        int paren = name.indexOf(')');
        this.features = (paren >= 0 && paren + 1 < name.length())
                ? name.substring(paren + 1).strip()
                : null;

        // Parse body fields
        String brand = null;
        for (String line : lines) {
            Matcher bm = BRAND_PAT.matcher(line);
            if (bm.find()) { brand = bm.group(1).strip(); break; }
        }
        this.cpuBrand = brand;
    }

    /** Total number of CPUs */
    @JsonIgnore public int totalCpus() { return totalCpus; }

    /** Initially active CPUs */
    @JsonIgnore public int initialActiveCpus() { return initialActiveCpus; }

    /** CPU features string after the "(initial active N)" part */
    @JsonIgnore public @Nullable String features() { return features; }

    /** macOS CPU brand (from machdep.cpu.brand_string), null on Linux */
    @JsonIgnore public @Nullable String cpuBrand() { return cpuBrand; }

    @JsonProperty public @NotNull String name() { return name; }
    @JsonProperty public @NotNull List<String> lines() { return lines; }

    @Override
    public void accept(HsErrVisitor v) { v.visitCpuInfo(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines) sb.append(line).append('\n');
        return sb.toString();
    }
}
