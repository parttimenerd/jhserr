package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thread info from "Current thread" line in THREAD section.
 */
public final class ThreadInfo implements ThreadSectionItem {
    private final String line;
    private final String address;
    private final String threadType;
    private final String name;
    private final String state;
    private final String id;

    @JsonCreator
    public ThreadInfo(@JsonProperty("line") String line,
                      @JsonProperty("address") String address,
                      @JsonProperty("threadType") String threadType,
                      @JsonProperty("name") String name,
                      @JsonProperty("state") String state,
                      @JsonProperty("id") String id) {
        this.line = line;
        this.address = address;
        this.threadType = threadType;
        this.name = name;
        this.state = state;
        this.id = id;
    }

    @JsonProperty public String line() { return line; }
    @JsonProperty public String address() { return address; }
    @JsonProperty public String threadType() { return threadType; }
    @JsonProperty public String name() { return name; }
    @JsonProperty public String state() { return state; }
    @JsonProperty public String id() { return id; }

    public void accept(HsErrVisitor v) { v.visitCurrentThread(this); }

    @Override
    public String toString() { return line + "\n"; }
}
