/**
 * Data model for HotSpot hs_err crash reports.
 *
 * <p>Contains the {@link me.bechberger.jhserr.model.SectionItem} sealed interface
 * and all its implementations (e.g.&nbsp;{@link me.bechberger.jhserr.model.BlankLine},
 * {@link me.bechberger.jhserr.model.NamedSection}, {@link me.bechberger.jhserr.model.ThreadInfo}),
 * as well as the top-level section containers
 * ({@link me.bechberger.jhserr.model.Header}, {@link me.bechberger.jhserr.model.Summary},
 * {@link me.bechberger.jhserr.model.ThreadSection}, {@link me.bechberger.jhserr.model.ProcessSection},
 * {@link me.bechberger.jhserr.model.SystemSection}).
 */
package me.bechberger.jhserr.model;
