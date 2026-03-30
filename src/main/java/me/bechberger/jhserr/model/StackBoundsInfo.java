package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed thread stack bounds from the THREAD section.
 *
 * <p>Format: {@code Stack: [0x00007fff60e00000,0x00007fff61000000],  sp=0x00007fff60ffccf0,  free space=2035k}
 */
public record StackBoundsInfo(
        @NotNull String stackBottom,
        @NotNull String stackTop,
        @Nullable String sp,
        @Nullable Long freeSpaceKb,
        boolean outsideStack
) implements ThreadSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "Stack:\\s*\\[(0x[0-9a-fA-F]+),(0x[0-9a-fA-F]+)]" +
            "(?:,\\s*sp=(0x[0-9a-fA-F]+))?" +
            "(?:,\\s*free space=(\\d+)k)?" +
            "(\\s*\\*\\*OUTSIDE STACK\\*\\*\\.)?");

    @JsonCreator
    public StackBoundsInfo(
            @JsonProperty("stackBottom") @NotNull String stackBottom,
            @JsonProperty("stackTop") @NotNull String stackTop,
            @JsonProperty("sp") @Nullable String sp,
            @JsonProperty("freeSpaceKb") @Nullable Long freeSpaceKb,
            @JsonProperty("outsideStack") boolean outsideStack) {
        this.stackBottom = stackBottom;
        this.stackTop = stackTop;
        this.sp = sp;
        this.freeSpaceKb = freeSpaceKb;
        this.outsideStack = outsideStack;
    }

    @Override public void accept(HsErrVisitor v) { v.visitStackBoundsInfo(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder("Stack: [").append(stackBottom).append(',').append(stackTop).append(']');
        if (sp != null) {
            sb.append(",  sp=").append(sp);
            if (outsideStack) {
                sb.append(" **OUTSIDE STACK**.");
            } else if (freeSpaceKb != null) {
                sb.append(",  free space=").append(freeSpaceKb).append('k');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    public static @Nullable StackBoundsInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        Long free = m.group(4) != null ? Long.parseLong(m.group(4)) : null;
        return new StackBoundsInfo(
                m.group(1),
                m.group(2),
                m.group(3),
                free,
                m.group(5) != null);
    }
}
