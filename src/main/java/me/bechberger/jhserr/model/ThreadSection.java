package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The THREAD section: crashing thread details.
 *
 * <p>Stores all sub-items in order as an {@link #items()} list. Each item is:
 * <ul>
 *   <li>{@link ThreadInfo} — "Current thread ..." line</li>
 *   <li>{@link FrameList} — "Native frames:" or "Java frames:" block</li>
 *   <li>{@link ErrorDuringError} — "[error occurred during error reporting ...]"</li>
 *   <li>{@link Registers} — "Registers:" block with register values</li>
 *   <li>{@link NamedSection} — any other named subsection (top of stack, instructions, etc.)</li>
 *   <li>{@link BlankLine} — separator blank line</li>
 * </ul>
 *
 * <p>The ordered list preserves exact interleaving and blank lines for faithful toString().
 * Convenience accessors extract typed items by scanning the list.
 */
public class ThreadSection {

    /** Section banner, e.g. "---------------  T H R E A D  ---------------" */
    private final @NotNull String banner;

    /** Ordered items: typed model objects or {@link BlankLine} separators */
    private final @NotNull List<ThreadSectionItem> items;

    @JsonCreator
    public ThreadSection(@JsonProperty("banner") @NotNull String banner,
                         @JsonProperty("items") @NotNull List<ThreadSectionItem> items) {
        this.banner = banner;
        this.items = List.copyOf(items);
    }

    @JsonProperty public @NotNull String banner() { return banner; }
    @JsonProperty public @NotNull List<ThreadSectionItem> items() { return items; }

    // ── generic finders ──

    @JsonIgnore
    public <T extends ThreadSectionItem> @Nullable T find(@NotNull Class<T> type) {
        for (ThreadSectionItem i : items) if (type.isInstance(i)) return type.cast(i);
        return null;
    }

    @JsonIgnore
    public <T extends ThreadSectionItem> @NotNull List<T> findAll(@NotNull Class<T> type) {
        var result = new ArrayList<T>();
        for (ThreadSectionItem i : items) if (type.isInstance(i)) result.add(type.cast(i));
        return result;
    }

    // ── convenience accessors ──

    @JsonIgnore public @Nullable ThreadInfo currentThread() { return find(ThreadInfo.class); }
    @JsonIgnore public @NotNull List<FrameList> frameLists() { return findAll(FrameList.class); }

    @JsonIgnore public @Nullable FrameList nativeFrames() {
        for (ThreadSectionItem i : items)
            if (i instanceof FrameList f && f.headerLine().startsWith("Native")) return f;
        return null;
    }

    @JsonIgnore public @Nullable FrameList javaFrames() {
        for (ThreadSectionItem i : items)
            if (i instanceof FrameList f && f.headerLine().startsWith("Java")) return f;
        return null;
    }

    @JsonIgnore public @Nullable Registers registers() { return find(Registers.class); }
    @JsonIgnore public @Nullable SignalInfo signalInfo() { return find(SignalInfo.class); }
    @JsonIgnore public @Nullable StackBoundsInfo stackBounds() { return find(StackBoundsInfo.class); }
    @JsonIgnore public @NotNull List<ErrorDuringError> errors() { return findAll(ErrorDuringError.class); }

    /** Returns only pure NamedSection items (not typed subclasses). */
    @JsonIgnore public @NotNull List<NamedSection> namedSections() {
        var result = new ArrayList<NamedSection>();
        for (SectionItem i : items) if (i.getClass() == NamedSection.class) result.add((NamedSection) i);
        return result;
    }

    public void accept(HsErrVisitor v) {
        v.visitThreadSection(this);
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
