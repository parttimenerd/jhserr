package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.bechberger.jhserr.HsErrVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-Safe Memory Reclamation (SMR) info from the PROCESS section.
 *
 * <p>Header: {@code Threads class SMR info:}
 */
public record SmrInfo(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("listBlocks") @NotNull List<AddressListBlock> listBlocks,
        @JsonProperty("metricGroups") @NotNull List<MetricGroup> metricGroups
) implements ProcessSectionItem {

    private static final Pattern LIST_HEADER = Pattern.compile(
            "^(\\S+)=(\\S+), length=(\\d+), elements=\\{\\s*$");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]+");

    @JsonCreator
    public SmrInfo(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("listBlocks") @Nullable List<AddressListBlock> listBlocks,
            @JsonProperty("metricGroups") @Nullable List<MetricGroup> metricGroups) {
        this.name = name;
        this.listBlocks = listBlocks != null ? listBlocks : new ArrayList<>();
        this.metricGroups = metricGroups != null ? metricGroups : new ArrayList<>();
    }

    /** Parser-compatible constructor. */
    public SmrInfo(@NotNull String name, @NotNull List<String> lines) {
        this(fromLines(name, lines));
    }

    public static @NotNull SmrInfo fromLines(@NotNull String name, @NotNull List<String> lines) {
        List<AddressListBlock> blocks = new ArrayList<>();
        List<MetricGroup> metrics = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher listMatcher = LIST_HEADER.matcher(line);
            if (listMatcher.matches()) {
                String listName = listMatcher.group(1);
                String listAddress = listMatcher.group(2);
                int length = parseInt(listMatcher.group(3));
                i++;

                List<String> addresses = new ArrayList<>();
                List<Integer> addressesPerLine = new ArrayList<>();
                while (i < lines.size() && !"}".equals(lines.get(i))) {
                    List<String> lineAddresses = parseAddresses(lines.get(i));
                    addresses.addAll(lineAddresses);
                    addressesPerLine.add(lineAddresses.size());
                    i++;
                }
                if (i < lines.size() && "}".equals(lines.get(i))) {
                    i++;
                }

                blocks.add(new AddressListBlock(listName, listAddress, length, addresses, addressesPerLine));
                continue;
            }

            if (!line.isBlank()) {
                metrics.add(parseMetricGroup(line));
            }
            i++;
        }

        return new SmrInfo(name, blocks, metrics);
    }

    private SmrInfo(@NotNull SmrInfo parsed) {
        this(parsed.name, parsed.listBlocks, parsed.metricGroups);
    }

    @JsonIgnore
    public @Nullable AddressListBlock javaThreadList() {
        for (AddressListBlock block : listBlocks) {
            if ("_java_thread_list".equals(block.listName())) {
                return block;
            }
        }
        return null;
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> out = new ArrayList<>();
        for (AddressListBlock block : listBlocks) {
            out.add(block.listName() + "=" + block.listAddress() + ", length=" + block.length() + ", elements={");
            int index = 0;
            for (Integer count : block.addressesPerLine()) {
                if (count == null || count <= 0) {
                    continue;
                }
                List<String> chunk = new ArrayList<>();
                for (int c = 0; c < count && index < block.addresses().size(); c++, index++) {
                    chunk.add(block.addresses().get(index));
                }
                boolean hasMore = index < block.addresses().size();
                out.add(String.join(", ", chunk) + (hasMore ? "," : ""));
            }
            out.add("}");
        }
        for (MetricGroup group : metricGroups) {
            out.add(group.renderLine());
        }
        return out;
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('\n');
        for (String line : lines()) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void accept(HsErrVisitor v) {
        v.visitSmrInfo(this);
    }

    private static @NotNull List<String> parseAddresses(@NotNull String line) {
        List<String> addresses = new ArrayList<>();
        Matcher matcher = ADDRESS.matcher(line);
        while (matcher.find()) {
            addresses.add(matcher.group());
        }
        return addresses;
    }

    private static @NotNull MetricGroup parseMetricGroup(@NotNull String line) {
        List<MetricEntry> entries = new ArrayList<>();
        int start = 0;
        while (start <= line.length()) {
            int next = line.indexOf(", ", start);
            String part;
            if (next >= 0) {
                part = line.substring(start, next);
            } else {
                part = line.substring(start);
            }

            int eq = part.indexOf('=');
            if (eq > 0) {
                entries.add(new MetricEntry(part.substring(0, eq), part.substring(eq + 1)));
            }

            if (next < 0) {
                break;
            }
            start = next + 2;
        }
        return new MetricGroup(entries);
    }

    private static int parseInt(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record AddressListBlock(
            @JsonProperty("listName") @NotNull String listName,
            @JsonProperty("listAddress") @NotNull String listAddress,
            @JsonProperty("length") int length,
            @JsonProperty("addresses") @NotNull List<String> addresses,
            @JsonProperty("addressesPerLine") @NotNull List<Integer> addressesPerLine
    ) {}

    public record MetricGroup(@JsonProperty("entries") @NotNull List<MetricEntry> entries) {
        public @NotNull String renderLine() {
            List<String> parts = new ArrayList<>(entries.size());
            for (MetricEntry entry : entries) {
                parts.add(entry.key() + "=" + entry.value());
            }
            return String.join(", ", parts);
        }
    }

    public record MetricEntry(
            @JsonProperty("key") @NotNull String key,
            @JsonProperty("value") @NotNull String value
    ) {}
}
