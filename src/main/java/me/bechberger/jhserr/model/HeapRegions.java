package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Heap region details from the PROCESS section.
 *
 * <p>Header: {@code Heap Regions: E=young(eden), S=young(survivor), ...}
 */
public final class HeapRegions extends NamedSection {

    @JsonCreator
    public HeapRegions(@JsonProperty("name") @NotNull String name,
                       @JsonProperty("lines") @Nullable List<String> lines) {
        super(name, lines != null ? lines : List.of());
    }

    public static @NotNull HeapRegions fromLines(@NotNull String name, @NotNull List<String> lines) {
        return new HeapRegions(name, lines);
    }

    @Override
    public void accept(HsErrVisitor v) { v.visitHeapRegions(this); }
}
