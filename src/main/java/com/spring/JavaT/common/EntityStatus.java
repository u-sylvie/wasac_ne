package com.spring.JavaT.common;

/**
 * Represents the lifecycle status of any entity that extends {@link BaseEntity}.
 *
 * <p>This is stored as a string column (not an ordinal) so that adding new values
 * later never shifts existing data in the database.
 *
 * <p>Status semantics:
 * <ul>
 *   <li>{@link #ACTIVE}    — the record is fully operational and visible.</li>
 *   <li>{@link #INACTIVE}  — the record has been disabled but not removed.
 *                            Useful for accounts that have been deactivated by an admin.</li>
 *   <li>{@link #SUSPENDED} — the record is temporarily blocked, typically pending
 *                            review or due to a policy violation.</li>
 *   <li>{@link #PENDING}   — the record has been created but not yet verified or
 *                            approved (e.g. email not confirmed).</li>
 * </ul>
 */
public enum EntityStatus {

    /** Fully operational and visible. */
    ACTIVE,

    /** Disabled but not deleted. */
    INACTIVE,

    /** Temporarily blocked. */
    SUSPENDED,

    /** Created but awaiting verification or approval. */
    PENDING,

    /** Meter disconnected due to prolonged non-payment. */
    DISCONNECTED
}
