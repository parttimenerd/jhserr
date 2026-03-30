package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A named sub-section: a header line followed by zero or more body lines.
 *
 * <p>Represents the many named blocks in hs_err files whose internal structure
 * is free-form or too varied to parse structurally. The semantic identity is
 * the header {@link #name()} — e.g. "Register to memory mapping:",
 * "Metaspace:", "Internal statistics:", etc.
 */
public non-sealed class NamedSection implements ThreadSectionItem, ProcessSectionItem, SystemSectionItem {

    private final @NotNull String name;
    private final @NotNull List<String> lines;

    @JsonCreator
    public NamedSection(@JsonProperty("name") @NotNull String name,
                        @JsonProperty("lines") @NotNull List<String> lines) {
        this.name = name;
        this.lines = lines != null ? List.copyOf(lines) : List.of();
    }

    @JsonProperty public @NotNull String name() { return name; }
    @JsonProperty public @NotNull List<String> lines() { return lines; }

    @Override
    public void accept(HsErrVisitor v) { v.visitNamedSection(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines) sb.append(line).append('\n');
        return sb.toString();
    }
}
