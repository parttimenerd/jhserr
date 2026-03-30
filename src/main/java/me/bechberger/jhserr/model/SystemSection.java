package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The SYSTEM section of the hs_err file.
 *
 * <p>Stores all sub-items in order as an {@link #items()} list (same approach
 * as ThreadSection and ProcessSection) for faithful round-trip toString().
 *
 * <p>Items are {@link OsInfo}, {@link CpuInfo}, {@link MemoryInfo},
 * {@link VmInfo}, other {@link NamedSection} sub-types, or {@link BlankLine} separators.
 */
public class SystemSection {

    /** Section banner, e.g. "---------------  S Y S T E M  ---------------" */
    private final @NotNull String banner;

    /** Ordered items: typed model objects or {@link BlankLine} separators */
    private final @NotNull List<SystemSectionItem> items;

    @JsonCreator
    public SystemSection(@JsonProperty("banner") @NotNull String banner,
                         @JsonProperty("items") @NotNull List<SystemSectionItem> items) {
        this.banner = banner;
        this.items = List.copyOf(items);
    }

    @JsonProperty public @NotNull String banner() { return banner; }
    @JsonProperty public @NotNull List<SystemSectionItem> items() { return items; }

    // ── generic finders ──

    @JsonIgnore
    public <T extends SystemSectionItem> @Nullable T find(@NotNull Class<T> type) {
        for (SystemSectionItem i : items) if (type.isInstance(i)) return type.cast(i);
        return null;
    }

    @JsonIgnore
    public <T extends SystemSectionItem> @NotNull List<T> findAll(@NotNull Class<T> type) {
        var result = new ArrayList<T>();
        for (SystemSectionItem i : items) if (type.isInstance(i)) result.add(type.cast(i));
        return result;
    }

    // ── convenience accessors ──

    @JsonIgnore public @Nullable OsInfo os() { return find(OsInfo.class); }
    @JsonIgnore public @Nullable CpuInfo cpu() { return find(CpuInfo.class); }
    @JsonIgnore public @Nullable MemoryInfo memory() { return find(MemoryInfo.class); }
    @JsonIgnore public @Nullable VmInfo vmInfo() { return find(VmInfo.class); }

    @JsonIgnore public @Nullable String pageSizesLine() {
        for (SystemSectionItem i : items) {
            if (i instanceof NamedSection g) {
                if (g.name().startsWith("Page sizes:") || g.name().startsWith("Page Sizes:")) return g.name();
                for (String line : g.lines())
                    if (line.startsWith("Page sizes:") || line.startsWith("Page Sizes:")) return line;
            }
        }
        return null;
    }

    public void accept(HsErrVisitor v) {
        v.visitSystemSection(this);
        for (SectionItem item : items) item.accept(v);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(banner).append('\n');
        for (SectionItem item : items) {
            if (item instanceof BlankLine) {
                sb.append('\n');
            } else {
                sb.append(item);
            }
        }
        return sb.toString();
    }
}
