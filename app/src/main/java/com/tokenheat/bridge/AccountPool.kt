package com.tokenheat.bridge

import com.tokenheat.proto.Credential
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.Wire

/**
 * Round-robin rotation over one slot's accounts with automatic failover.
 *
 * The pool is deliberately dumb: it cycles through every enabled account and
 * marks failures by kind (credit exhaustion parks the account, rate limits
 * cool it down, dead sessions retire it). When every account is parked the
 * pool does one recovery sweep instead of refusing — quotas reset upstream,
 * so a cold refusal would stick until restart.
 *
 * Only the manual `disabled` flag is persisted (in the account file). Failure
 * marks are process-local: a restart re-probes everything, which is the
 * correct behavior after quotas may have reset. All entry points are
 * synchronized because request threads share the pool.
 *
 * Scope is one slot (provider + WorkBuddy build): account ids carry the slot
 * prefix, so marks never leak across slots even though one pool instance is
 * shared.
 */
class AccountPool(
    private val candidates: () -> List<SavedAccount>,
    private val refresher: (SavedAccount) -> Credential?,
) {

    /** Transient health of one account; see class docs for persistence. */
    enum class State { ACTIVE, EXHAUSTED, COOLING, INVALID }

    /** One serving turn: the account plus a fresh credential for it. */
    data class Pick(val account: SavedAccount, val credential: Credential)

    private data class Entry(
        var state: State = State.ACTIVE,
        var coolingUntil: Long = 0L,
        var lastError: String = "",
    )

    private val lock = Any()
    private val states = HashMap<String, Entry>()
    private var cursor = 0

    /**
     * Returns the next eligible account, or null when the slot holds no
     * enabled account at all. A null credential (unresolvable account) skips
     * to the next candidate rather than failing the request.
     */
    fun pick(): Pick? = synchronized(lock) {
        val all = candidates().filterNot { it.disabled }
        if (all.isEmpty()) return null
        normalize()
        var eligible = all.filter { entryFor(it.id).eligible() }
        if (eligible.isEmpty()) {
            states.clear()
            eligible = all
        }
        for (step in all.indices) {
            val candidate = all[(cursor + step) % all.size]
            if (eligible.any { it.id == candidate.id }) {
                cursor = (cursor + step + 1) % all.size
                val credential = refresher(candidate) ?: continue
                return Pick(candidate, credential)
            }
        }
        return null
    }

    /** Records one attempt's outcome; see class docs for the mapping. */
    fun report(id: String, kind: Wire.ErrorKind, status: Int) = synchronized(lock) {
        val entry = entryFor(id)
        when (kind) {
            Wire.ErrorKind.HARD_CREDIT -> {
                entry.state = State.EXHAUSTED
                entry.lastError = "credit exhausted"
            }
            Wire.ErrorKind.SOFT_RATE -> {
                entry.state = State.COOLING
                entry.coolingUntil = System.currentTimeMillis() + COOLING_MS
                entry.lastError = "rate limited"
            }
            Wire.ErrorKind.SESSION_DEAD -> {
                entry.state = State.INVALID
                entry.lastError = "auth refused ($status)"
            }
            // SERVER is transient and CLIENT/NOT_FOUND are request-scoped:
            // neither says anything about the account, so neither marks it.
            Wire.ErrorKind.SERVER,
            Wire.ErrorKind.CLIENT,
            Wire.ErrorKind.NOT_FOUND,
            -> entry.lastError = "transient ($status)"
        }
    }

    /**
     * Whether a failure is worth retrying on another account inside the same
     * request. Credit/rate/auth/server failures may clear on a different
     * account; malformed requests and unknown models fail identically
     * everywhere, so retrying would only burn quota.
     */
    fun shouldFailover(kind: Wire.ErrorKind): Boolean = when (kind) {
        Wire.ErrorKind.HARD_CREDIT,
        Wire.ErrorKind.SOFT_RATE,
        Wire.ErrorKind.SESSION_DEAD,
        Wire.ErrorKind.SERVER,
        -> true
        Wire.ErrorKind.CLIENT,
        Wire.ErrorKind.NOT_FOUND,
        -> false
    }

    /** Eligible accounts right now, for `/healthz`. */
    fun eligibleCount(): Int = synchronized(lock) {
        normalize()
        candidates().filterNot { it.disabled }.count { entryFor(it.id).eligible() }
    }

    private fun entryFor(id: String): Entry = states.getOrPut(id) { Entry() }

    private fun normalize() {
        val now = System.currentTimeMillis()
        states.values.forEach {
            if (it.state == State.COOLING && now >= it.coolingUntil) {
                it.state = State.ACTIVE
                it.coolingUntil = 0L
                it.lastError = ""
            }
        }
    }

    private fun Entry.eligible(): Boolean = when (state) {
        State.ACTIVE -> true
        // normalize() runs before every read, so an expired cooldown never
        // survives to here; the check is belt and braces.
        State.COOLING -> System.currentTimeMillis() >= coolingUntil
        State.EXHAUSTED, State.INVALID -> false
    }

    companion object {
        /** How long a rate-limited account sits out. */
        const val COOLING_MS = 5 * 60 * 1000L
    }
}
