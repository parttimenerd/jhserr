package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed uid/euid/gid/egid from a PROCESS section line.
 *
 * <p>Format: {@code uid  : N euid : N gid  : N egid : N}
 */
public record UidInfo(int uid, int euid, int gid, int egid) implements ProcessSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "uid\\s*:\\s*(\\d+)\\s+euid\\s*:\\s*(\\d+)\\s+gid\\s*:\\s*(\\d+)\\s+egid\\s*:\\s*(\\d+)");

    @JsonCreator
    public UidInfo(@JsonProperty("uid") int uid, @JsonProperty("euid") int euid,
                   @JsonProperty("gid") int gid, @JsonProperty("egid") int egid) {
        this.uid = uid;
        this.euid = euid;
        this.gid = gid;
        this.egid = egid;
    }

    @Override public void accept(HsErrVisitor v) { v.visitUidInfo(this); }

    @Override
    public String toString() {
        return "uid  : %d euid : %d gid  : %d egid : %d\n".formatted(uid, euid, gid, egid);
    }

    public static @Nullable UidInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new UidInfo(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
    }
}
