package com.tokenheat.proto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One saved account. A build can hold several of these, so identity is carried
 * explicitly rather than inferred from the slot it sits in.
 */
data class SavedAccount(
    val id: String,
    val provider: Provider = Provider.WORKBUDDY,
    val region: Wire.Region,
    val nickname: String,
    val uid: String,
    val domain: String,
    val accessToken: String,
    val refreshToken: String,
    val apiKey: String = "",
    /** Manual opt-out from rotation; the row stays usable by hand. */
    val disabled: Boolean = false,
    val expiresAt: Long,
    val enterpriseId: String? = null,
    val source: String = "oauth",
) {
    /** Display label: the nickname, falling back to a short uid. */
    val label: String get() = nickname.ifBlank { uid.take(8) }

    fun toCredential(): Credential = Credential(
        provider = provider,
        accessToken = accessToken,
        refreshToken = refreshToken,
        apiKey = apiKey,
        expiresAt = expiresAt,
        domain = domain,
        uid = uid,
        nickname = nickname,
        enterpriseId = enterpriseId,
        source = source,
    )
}

/**
 * Stores the accounts of each build and remembers which one is in use.
 *
 * Each build owns one file holding an array of accounts, so adding a second
 * account never disturbs the first. The active selection is a pair of
 * build and account id, persisted separately.
 */
class CredentialStore(context: Context) {

    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("tokenheat", Context.MODE_PRIVATE)

    private fun fileFor(region: Wire.Region): File =
        File(dir, "tokenheat-auth-${region.name.lowercase()}.json")

    /** ZCode owns its own slot file; WorkBuddy keeps the per-build files. */
    private fun fileForProvider(provider: Provider): File =
        File(dir, "tokenheat-auth-${provider.name.lowercase()}.json")

    // ------------------------------------------------------------------ //
    // Accounts
    // ------------------------------------------------------------------ //

    /** Every WorkBuddy account saved for one build. */
    fun accounts(region: Wire.Region): List<SavedAccount> =
        accountsIn(fileFor(region), region, Provider.WORKBUDDY)

    /** Every ZCode account. Its `region` is always CN and unused for routing. */
    fun zcodeAccounts(): List<SavedAccount> =
        accountsIn(fileForProvider(Provider.ZCODE), Wire.Region.CN, Provider.ZCODE)

    /** Every Zen account. Its `region` is always CN and unused for routing. */
    fun zenAccounts(): List<SavedAccount> =
        accountsIn(fileForProvider(Provider.ZEN), Wire.Region.CN, Provider.ZEN)

    /**
     * Every account of one slot. All mutations must go through here (and
     * [writeAccounts]) — branching on a single provider inline is how Zen
     * keys once ended up in the WorkBuddy file.
     */
    private fun accountsOf(provider: Provider, region: Wire.Region): List<SavedAccount> = when (provider) {
        Provider.ZCODE -> zcodeAccounts()
        Provider.ZEN -> zenAccounts()
        Provider.WORKBUDDY -> accounts(region)
    }

    private fun accountsIn(file: File, region: Wire.Region, provider: Provider): List<SavedAccount> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { readAccount(array.optJSONObject(it), region, provider) }
        }.getOrElse {
            // An older build wrote a single credential object at this path.
            readLegacy(file, region, provider)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun readAccount(obj: JSONObject?, region: Wire.Region, provider: Provider): SavedAccount? {
        obj ?: return null
        val token = obj.optString("accessToken")
        val apiKey = obj.optString("apiKey")
        // WorkBuddy authenticates with access tokens; an apiKey-only entry in
        // a build file is a misplaced foreign key, not a login — drop it so a
        // stale miswrite can never surface as a phantom account.
        if (token.isEmpty() && (provider == Provider.WORKBUDDY || apiKey.isEmpty())) return null
        val domain = obj.optString("domain").ifEmpty { defaultDomain(region) }
        val uid = obj.optString("uid")
        val stored = obj.optLong("expiresAt", 0L)
        return SavedAccount(
            id = obj.optString("id").ifEmpty { accountId(activeSlot(provider, region), uid, domain, token.ifEmpty { apiKey }) },
            provider = provider,
            region = region,
            nickname = obj.optString("nickname"),
            uid = uid,
            domain = domain,
            accessToken = token,
            refreshToken = obj.optString("refreshToken"),
            apiKey = apiKey,
            disabled = obj.optBoolean("disabled", false),
            expiresAt = if (stored > 0) stored else expiryFromJwt(token),
            enterpriseId = obj.optString("enterpriseId").ifEmpty { null },
            source = obj.optString("source", "oauth"),
        )
    }

    /**
     * Reads the single-object shape written before multi-account support, so an
     * existing install does not lose its credential on upgrade.
     */
    private fun readLegacy(file: File, region: Wire.Region, provider: Provider): SavedAccount? = runCatching {
        val obj = JSONObject(file.readText())
        val token = obj.optString("accessToken")
        if (token.isEmpty()) return null
        val domain = obj.optString("domain").ifEmpty { defaultDomain(region) }
        val uid = obj.optString("uid")
        val stored = obj.optLong("expiresAt", 0L)
        SavedAccount(
            id = accountId(region.name, uid, domain),
            provider = provider,
            region = region,
            nickname = obj.optString("nickname"),
            uid = uid,
            domain = domain,
            accessToken = token,
            refreshToken = obj.optString("refreshToken"),
            apiKey = obj.optString("apiKey"),
            expiresAt = if (stored > 0) stored else expiryFromJwt(token),
            enterpriseId = obj.optString("enterpriseId").ifEmpty { null },
            source = obj.optString("source", "oauth"),
        )
    }.getOrNull()

    private fun writeAccounts(region: Wire.Region, provider: Provider, accounts: List<SavedAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("id", account.id)
                    put("accessToken", account.accessToken)
                    put("refreshToken", account.refreshToken)
                    put("apiKey", account.apiKey)
                    put("disabled", account.disabled)
                    put("expiresAt", account.expiresAt)
                    put("domain", account.domain)
                    put("uid", account.uid)
                    put("nickname", account.nickname)
                    put("enterpriseId", account.enterpriseId ?: "")
                    put("source", account.source)
                },
            )
        }
        (if (provider == Provider.WORKBUDDY) fileFor(region) else fileForProvider(provider)).writeText(array.toString())
    }

    /**
     * Adds a credential, or replaces the one with the same account when it is
     * already stored. Replacing rather than duplicating means signing in again
     * renews a token instead of creating a confusing second entry.
     */
    fun save(region: Wire.Region, credential: Credential): SavedAccount =
        saveIn(region, Provider.WORKBUDDY, credential)

    /** Adds a ZCode credential to its own slot. */
    fun saveZcode(credential: Credential): SavedAccount =
        saveIn(Wire.Region.CN, Provider.ZCODE, credential)

    /** Adds a Zen API key to its own slot. */
    fun saveZen(credential: Credential): SavedAccount =
        saveIn(Wire.Region.CN, Provider.ZEN, credential)

    private fun saveIn(region: Wire.Region, provider: Provider, credential: Credential): SavedAccount {
        val seed = credential.accessToken.ifEmpty { credential.apiKey }
        // The id slot matches the selection slot, so re-signing replaces the
        // old entry instead of orphaning it (WorkBuddy ids stay "CN:…").
        val id = accountId(activeSlot(provider, region), credential.uid, credential.domain, seed)
        val current = accountsOf(provider, region).toMutableList()
        // Re-signing renews the token but keeps a manual rotation opt-out.
        val keepDisabled = current.firstOrNull { it.id == id }?.disabled ?: false
        val account = SavedAccount(
            id = id,
            provider = provider,
            region = region,
            nickname = credential.nickname,
            uid = credential.uid,
            domain = credential.domain.ifEmpty { defaultDomain(region) },
            accessToken = credential.accessToken,
            refreshToken = credential.refreshToken,
            apiKey = credential.apiKey,
            disabled = keepDisabled,
            expiresAt = credential.expiresAt,
            enterpriseId = credential.enterpriseId,
            source = credential.source,
        )
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) current[index] = account else current.add(account)
        writeAccounts(region, provider, current)
        setActiveSlot(activeSlot(provider, region), id)
        return account
    }

    /** Removes one account; the selection falls back to another one. */
    fun delete(region: Wire.Region, accountId: String) {
        val remaining = accounts(region).filterNot { it.id == accountId }
        writeAccounts(region, Provider.WORKBUDDY, remaining)
        if (activeId(region) == accountId) {
            remaining.firstOrNull()?.let { setActive(region, it.id) }
                ?: clearActive(region)
        }
    }

    /** Removes one ZCode account; the selection falls back to another one. */
    fun deleteZcode(accountId: String) {
        val remaining = zcodeAccounts().filterNot { it.id == accountId }
        writeAccounts(Wire.Region.CN, Provider.ZCODE, remaining)
        val slot = activeSlot(Provider.ZCODE, Wire.Region.CN)
        if (activeSlotId(slot) == accountId) {
            remaining.firstOrNull()?.let { setActiveSlot(slot, it.id) }
                ?: clearActiveSlot(slot)
        }
    }

    /** Removes one Zen account; the selection falls back to another one. */
    fun deleteZen(accountId: String) {
        val remaining = zenAccounts().filterNot { it.id == accountId }
        writeAccounts(Wire.Region.CN, Provider.ZEN, remaining)
        val slot = activeSlot(Provider.ZEN, Wire.Region.CN)
        if (activeSlotId(slot) == accountId) {
            remaining.firstOrNull()?.let { setActiveSlot(slot, it.id) }
                ?: clearActiveSlot(slot)
        }
    }

    fun clear(region: Wire.Region) {
        fileFor(region).delete()
        clearActive(region)
    }

    /** Drops every ZCode account and its selection. */
    fun clearZcode() {
        fileForProvider(Provider.ZCODE).delete()
        clearActiveSlot(activeSlot(Provider.ZCODE, Wire.Region.CN))
    }

    /** Drops every Zen account and its selection. */
    fun clearZen() {
        fileForProvider(Provider.ZEN).delete()
        clearActiveSlot(activeSlot(Provider.ZEN, Wire.Region.CN))
    }

    /**
     * One-time repair for keys the pre-fix writer saved into a build file
     * (Zen went to the CN file, so it listed under WorkBuddy). Moves
     * apiKey-only entries to the Zen slot, preserving nickname/label.
     * Idempotent: a clean tree is a no-op, so both entry points call it.
     */
    fun repairMisplacedApiKeys() {
        val zen = zenAccounts().toMutableList()
        var zenDirty = false
        Wire.Region.entries.forEach { region ->
            val file = fileFor(region)
            if (!file.exists()) return@forEach
            val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@forEach
            val keep = JSONArray()
            var dirty = false
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("accessToken").isEmpty() && obj.optString("apiKey").isNotEmpty()) {
                    val apiKey = obj.optString("apiKey")
                    val id = obj.optString("id")
                        .ifEmpty { accountId("ZEN", obj.optString("uid"), obj.optString("domain"), apiKey) }
                    if (zen.none { it.id == id }) {
                        zen += SavedAccount(
                            id = id,
                            provider = Provider.ZEN,
                            region = Wire.Region.CN,
                            nickname = obj.optString("nickname").ifEmpty { "Zen" },
                            uid = obj.optString("uid"),
                            domain = obj.optString("domain").ifEmpty { ZenUpstreamClient.CHAT_BASE },
                            accessToken = "",
                            refreshToken = "",
                            apiKey = apiKey,
                            disabled = obj.optBoolean("disabled", false),
                            expiresAt = obj.optLong("expiresAt", 0L),
                            source = obj.optString("source", "manual-zen"),
                        )
                    }
                    dirty = true
                } else {
                    keep.put(obj)
                }
            }
            if (dirty) {
                file.writeText(keep.toString())
                zenDirty = true
            }
        }
        if (zenDirty) writeAccounts(Wire.Region.CN, Provider.ZEN, zen)
    }

    /**
     * Stable id for an account. The uid identifies a person, so it is preferred;
     * a credential without one falls back to its domain plus a token digest so
     * two such accounts still get distinct ids.
     */
    private fun accountId(slot: String, uid: String, domain: String, accessToken: String = ""): String =
        if (uid.isNotBlank()) "$slot:${uid.take(8)}"
        else "$slot:${domain}:${accessToken.hashCode()}"

    // ------------------------------------------------------------------ //
    // Selection
    // ------------------------------------------------------------------ //

    /** The build currently in use. */
    fun activeRegion(): Wire.Region {
        val stored = prefs.getString(KEY_ACTIVE_REGION, null) ?: return Wire.Region.CN
        return runCatching { Wire.Region.valueOf(stored) }.getOrDefault(Wire.Region.CN)
    }

    fun setActiveRegion(region: Wire.Region) {
        prefs.edit().putString(KEY_ACTIVE_REGION, region.name).apply()
        mirrorActiveForDaemon()
    }

    /**
     * Selects which saved account a build uses. The id is ignored when it does
     * not belong to the build, so a stale selection cannot leak across.
     */
    /** Looks one account up by id, or null when it is no longer stored. */
    fun account(region: Wire.Region, accountId: String): SavedAccount? =
        accounts(region).firstOrNull { it.id == accountId }

    fun selectAccount(region: Wire.Region, accountId: String) {
        if (accounts(region).none { it.id == accountId }) return
        setActive(region, accountId)
    }

    private fun activeId(region: Wire.Region): String? = prefs.getString("$KEY_ACTIVE_ID${region.name}", null)

    private fun setActive(region: Wire.Region, accountId: String) {
        prefs.edit().putString("$KEY_ACTIVE_ID${region.name}", accountId).apply()
        mirrorActiveForDaemon()
    }

    private fun clearActive(region: Wire.Region) {
        prefs.edit().remove("$KEY_ACTIVE_ID${region.name}").apply()
    }

    /** Prefs slot holding one provider's (or build's) selected account. */
    private fun activeSlot(provider: Provider, region: Wire.Region): String =
        when (provider) {
            Provider.ZCODE -> "ZCODE"
            Provider.ZEN -> "ZEN"
            Provider.WORKBUDDY -> region.name
        }

    private fun activeSlotId(slot: String): String? = prefs.getString("$KEY_ACTIVE_ID$slot", null)

    private fun setActiveSlot(slot: String, accountId: String) {
        prefs.edit().putString("$KEY_ACTIVE_ID$slot", accountId).apply()
        mirrorActiveForDaemon()
    }

    private fun clearActiveSlot(slot: String) {
        prefs.edit().remove("$KEY_ACTIVE_ID$slot").apply()
    }

    /** Which upstream account system the bridge currently serves. */
    fun activeProvider(): Provider {
        val stored = prefs.getString(KEY_ACTIVE_PROVIDER, null) ?: return Provider.WORKBUDDY
        return runCatching { Provider.valueOf(stored) }.getOrDefault(Provider.WORKBUDDY)
    }

    fun setActiveProvider(provider: Provider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
        mirrorActiveForDaemon()
    }

    /** Selects the ZCode account in use; unknown ids are ignored. */
    fun selectZcodeAccount(accountId: String) {
        if (zcodeAccounts().none { it.id == accountId }) return
        setActiveSlot(activeSlot(Provider.ZCODE, Wire.Region.CN), accountId)
    }

    /** Selects the Zen account in use; unknown ids are ignored. */
    fun selectZenAccount(accountId: String) {
        if (zenAccounts().none { it.id == accountId }) return
        setActiveSlot(activeSlot(Provider.ZEN, Wire.Region.CN), accountId)
    }

    fun zcodeActiveId(): String? = activeSlotId(activeSlot(Provider.ZCODE, Wire.Region.CN))

    fun zenActiveId(): String? = activeSlotId(activeSlot(Provider.ZEN, Wire.Region.CN))

    /** The ZCode account in use, or null when none is stored. */
    fun activeZcodeAccount(): SavedAccount? {
        val list = zcodeAccounts()
        if (list.isEmpty()) return null
        val selected = zcodeActiveId()
        return list.firstOrNull { it.id == selected } ?: list.first()
    }

    fun activeZcode(): Credential? = activeZcodeAccount()?.toCredential()

    /** The Zen account in use, or null when none is stored. */
    fun activeZenAccount(): SavedAccount? {
        val list = zenAccounts()
        if (list.isEmpty()) return null
        val selected = zenActiveId()
        return list.firstOrNull { it.id == selected } ?: list.first()
    }

    fun activeZen(): Credential? = activeZenAccount()?.toCredential()

    /** The credential the bridge should serve under the current provider. */
    fun effectiveActive(): Credential? = when (activeProvider()) {
        Provider.ZCODE -> activeZcode()
        Provider.ZEN -> activeZen()
        Provider.WORKBUDDY -> active()
    }

    /** The account in use, or null when that build holds none. */
    fun active(): Credential? = activeAccount()?.toCredential()

    fun activeAccount(): SavedAccount? {
        val region = activeRegion()
        val list = accounts(region)
        if (list.isEmpty()) return null
        val selected = activeId(region)
        return list.firstOrNull { it.id == selected } ?: list.first()
    }

    /** Whether a build has at least one saved account. */
    fun has(region: Wire.Region): Boolean = accounts(region).isNotEmpty()

    fun hasAny(): Boolean =
        Wire.Region.entries.any { has(it) } || zcodeAccounts().isNotEmpty() || zenAccounts().isNotEmpty()

    // ------------------------------------------------------------------ //
    // Refresh
    // ------------------------------------------------------------------ //

    fun needsRefresh(credential: Credential): Boolean {
        if (credential.expiresAt <= 0) return true
        val nowSeconds = System.currentTimeMillis() / 1000
        return nowSeconds + REFRESH_MARGIN_SECONDS >= credential.expiresAt
    }

    /**
     * Returns a credential safe to send, renewing first when it is inside the
     * margin. The refreshed token is written back to the account it came from,
     * so a switch back does not serve a stale token.
     */
    @Synchronized
    fun resolve(renew: (Credential) -> Credential): Credential? {
        val account = activeAccount() ?: return null
        return resolveAccount(account, renew)
    }

    /**
     * Same as [resolve] but for an explicit pool member, so rotation can serve
     * a fresh credential for whichever account is picked — not just the
     * selected one. Synchronized because request threads share the pool.
     */
    @Synchronized
    fun resolveAccount(account: SavedAccount, renew: (Credential) -> Credential): Credential? {
        if (!needsRefresh(account.toCredential())) return account.toCredential()
        return runCatching {
            val renewed = renew(account.toCredential())
            val current = accountsOf(account.provider, account.region)
            val updated = current.map {
                if (it.id == account.id) it.copy(
                    accessToken = renewed.accessToken,
                    refreshToken = renewed.refreshToken,
                    expiresAt = renewed.expiresAt,
                    domain = renewed.domain,
                ) else it
            }
            writeAccounts(account.region, account.provider, updated)
            renewed
        }.getOrElse {
            // A failed renewal still returns the existing token when it has not
            // yet expired, so an unreachable refresh endpoint does not take a
            // working session down.
            android.util.Log.w("TokenHeat", "token refresh failed; using existing token", it)
            account.toCredential()
        }
    }

    /**
     * Takes one account out of (or returns it to) rotation. The row stays for
     * manual selection, balance checks and check-in; only serving skips it.
     */
    fun setDisabled(account: SavedAccount, disabled: Boolean) {
        val current = accountsOf(account.provider, account.region).toMutableList()
        val index = current.indexOfFirst { it.id == account.id }
        if (index < 0) return
        current[index] = current[index].copy(disabled = disabled)
        writeAccounts(account.region, account.provider, current)
    }

    /**
     * Publishes the account in use to the path a local consumer reads.
     * Only the active account is mirrored, which keeps the others' tokens out of
     * a world-readable location.
     */
    fun mirrorActiveForDaemon() {
        val target = File(DAEMON_AUTH_PATH)
        val credential = effectiveActive()
        runCatching {
            target.parentFile?.mkdirs()
            if (credential == null) {
                if (target.exists()) target.delete()
                return@runCatching
            }
            val json = JSONObject().apply {
                put("provider", credential.provider.name)
                put("accessToken", credential.accessToken)
                put("refreshToken", credential.refreshToken)
                put("apiKey", credential.apiKey)
                put("expiresAt", credential.expiresAt)
                put("domain", credential.domain)
                put("uid", credential.uid)
                put("nickname", credential.nickname)
                put("enterpriseId", credential.enterpriseId ?: "")
            }
            target.writeText(json.toString(2))
            // A local consumer may run as another user, so the copy has to be
            // readable by it.
            target.setReadable(true, false)
        }
    }

    private fun defaultDomain(region: Wire.Region): String =
        if (region == Wire.Region.GLOBAL) "www.workbuddy.ai" else Wire.CN_CHAT_BASE

    private fun expiryFromJwt(token: String): Long {
        val parts = token.split(".")
        if (parts.size < 2) return 0L
        return runCatching {
            val bytes = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            )
            JSONObject(String(bytes, Charsets.UTF_8)).optLong("exp", 0L)
        }.getOrDefault(0L)
    }

    companion object {
        /** Where a local consumer expects the account in use. */
        const val DAEMON_AUTH_PATH = "/data/local/tmp/tokenheat/tokenheat-auth.json"

        private const val KEY_ACTIVE_REGION = "active_region"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_ACTIVE_ID = "active_account_"

        /** Renew this long before the token actually expires. */
        private const val REFRESH_MARGIN_SECONDS = 5 * 60L
    }
}
