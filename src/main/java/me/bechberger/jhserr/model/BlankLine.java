package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * A blank separator line between items in a section.
 *
 * <p>All blank lines in hs_err files serve as visual separators between
 * distinct blocks. {@link #toString()} returns an empty string; the
 * containing section appends the newline.
 */
public record BlankLine() implements ThreadSectionItem, ProcessSectionItem, SystemSectionItem {
    @JsonCreator public BlankLine {}
    @Override public void accept(HsErrVisitor v) { v.visitBlankLine(this); }
    @Override public String toString() { return ""; }
}
