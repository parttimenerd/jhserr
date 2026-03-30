package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed memory information from the SYSTEM section.
 *
 * <p>Format: {@code Memory: 16k page, physical 16777216k(324576k free), swap 16777216k(582208k free)}
 *
 * <p>Also handles extended Linux format with page sizes.
 */
public record MemoryInfo(
        @Nullable String pageSize,
        @Nullable Long physicalKb,
        @Nullable Long physicalFreeKb,
        @Nullable Long swapKb,
        @Nullable Long swapFreeKb
) implements SystemSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "Memory:\\s*(\\S+)\\s+page,\\s*physical\\s+(\\d+)k\\((\\d+)k\\s+free\\)" +
            "(?:,\\s*swap\\s+(\\d+)k\\((\\d+)k\\s+free\\))?");

    @JsonCreator
    public MemoryInfo(
            @JsonProperty("pageSize") @Nullable String pageSize,
            @JsonProperty("physicalKb") @Nullable Long physicalKb,
            @JsonProperty("physicalFreeKb") @Nullable Long physicalFreeKb,
            @JsonProperty("swapKb") @Nullable Long swapKb,
            @JsonProperty("swapFreeKb") @Nullable Long swapFreeKb) {
        this.pageSize = pageSize;
        this.physicalKb = physicalKb;
        this.physicalFreeKb = physicalFreeKb;
        this.swapKb = swapKb;
        this.swapFreeKb = swapFreeKb;
    }

    @Override public void accept(HsErrVisitor v) { v.visitMemoryInfo(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder("Memory: ");
        if (pageSize != null) sb.append(pageSize).append(" page, ");
        if (physicalKb != null) {
            sb.append("physical ").append(physicalKb).append("k");
            if (physicalFreeKb != null) sb.append('(').append(physicalFreeKb).append("k free)");
        }
        if (swapKb != null) {
            sb.append(", swap ").append(swapKb).append("k");
            if (swapFreeKb != null) sb.append('(').append(swapFreeKb).append("k free)");
        }
        sb.append('\n');
        return sb.toString();
    }

    public static @Nullable MemoryInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new MemoryInfo(
                m.group(1),
                Long.parseLong(m.group(2)),
                Long.parseLong(m.group(3)),
                m.group(4) != null ? Long.parseLong(m.group(4)) : null,
                m.group(5) != null ? Long.parseLong(m.group(5)) : null);
    }
}
