package com.db20g.controller.protocol

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages channel groups (banks), favorites, and channel organization.
 *
 * Features:
 * - Named channel groups (e.g., "Local Repeaters", "Family", "Travel")
 * - Favorite channels for quick access
 * - Backup/restore channel configurations as JSON
 * - Persists to SharedPreferences
 */
class ChannelGroupManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "channel_groups"
        private const val KEY_GROUPS = "groups_json"
        private const val KEY_FAVORITES = "favorites"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Channel Groups ---

    data class ChannelGroup(
        val id: String,
        val name: String,
        val channelNumbers: List<Int>,
        val color: Int = 0xFF1B5E20.toInt()
    )

    fun getGroups(): List<ChannelGroup> {
        val json = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val channels = obj.getJSONArray("channels")
                ChannelGroup(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    channelNumbers = (0 until channels.length()).map { channels.getInt(it) },
                    color = obj.optInt("color", 0xFF1B5E20.toInt())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveGroups(groups: List<ChannelGroup>) {
        val arr = JSONArray()
        for (group in groups) {
            val obj = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("channels", JSONArray(group.channelNumbers))
                put("color", group.color)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    fun addGroup(name: String, channelNumbers: List<Int> = emptyList(), color: Int = 0xFF1B5E20.toInt()): ChannelGroup {
        val groups = getGroups().toMutableList()
        val id = "group_${System.currentTimeMillis()}"
        val group = ChannelGroup(id, name, channelNumbers, color)
        groups.add(group)
        saveGroups(groups)
        return group
    }

    fun updateGroup(groupId: String, name: String? = null, channelNumbers: List<Int>? = null, color: Int? = null) {
        val groups = getGroups().toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            val old = groups[idx]
            groups[idx] = ChannelGroup(
                id = old.id,
                name = name ?: old.name,
                channelNumbers = channelNumbers ?: old.channelNumbers,
                color = color ?: old.color
            )
            saveGroups(groups)
        }
    }

    fun deleteGroup(groupId: String) {
        val groups = getGroups().toMutableList()
        groups.removeAll { it.id == groupId }
        saveGroups(groups)
    }

    fun addChannelToGroup(groupId: String, channelNumber: Int) {
        val groups = getGroups().toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            val old = groups[idx]
            if (channelNumber !in old.channelNumbers) {
                groups[idx] = old.copy(channelNumbers = old.channelNumbers + channelNumber)
                saveGroups(groups)
            }
        }
    }

    fun removeChannelFromGroup(groupId: String, channelNumber: Int) {
        val groups = getGroups().toMutableList()
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            val old = groups[idx]
            groups[idx] = old.copy(channelNumbers = old.channelNumbers - channelNumber)
            saveGroups(groups)
        }
    }

    fun getGroupsForChannel(channelNumber: Int): List<ChannelGroup> {
        return getGroups().filter { channelNumber in it.channelNumbers }
    }

    // --- Favorites ---

    fun getFavorites(): Set<Int> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()
    }

    fun toggleFavorite(channelNumber: Int) {
        val favorites = getFavorites().toMutableSet()
        if (channelNumber in favorites) {
            favorites.remove(channelNumber)
        } else {
            favorites.add(channelNumber)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet()).apply()
    }

    fun isFavorite(channelNumber: Int): Boolean {
        return channelNumber in getFavorites()
    }

    // --- Backup / Restore ---

    /**
     * Create a JSON backup of all channels, groups, and favorites.
     */
    fun createBackup(channels: List<RadioChannel>): String {
        val root = JSONObject()

        // Channels
        val channelsArr = JSONArray()
        for (ch in channels) {
            if (ch.isEmpty) continue
            val obj = JSONObject().apply {
                put("number", ch.number)
                put("name", ch.name)
                put("rxFrequency", ch.rxFrequency)
                put("txFrequency", ch.txFrequency)
                put("rxTone", toneToJson(ch.rxTone))
                put("txTone", toneToJson(ch.txTone))
                put("power", ch.power.name)
                put("wideband", ch.wideband)
                put("bcl", ch.bcl)
                put("scan", ch.scan)
            }
            channelsArr.put(obj)
        }
        root.put("channels", channelsArr)

        // Groups
        val groupsArr = JSONArray()
        for (group in getGroups()) {
            val obj = JSONObject().apply {
                put("name", group.name)
                put("channels", JSONArray(group.channelNumbers))
                put("color", group.color)
            }
            groupsArr.put(obj)
        }
        root.put("groups", groupsArr)

        // Favorites
        root.put("favorites", JSONArray(getFavorites().toList()))

        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        return root.toString(2)
    }

    /**
     * Restore channels, groups, and favorites from a JSON backup.
     * Returns the list of channels to apply.
     */
    fun restoreBackup(json: String): RestoreResult {
        return try {
            val root = JSONObject(json)
            val channels = mutableListOf<RadioChannel>()

            // Parse channels
            val channelsArr = root.getJSONArray("channels")
            for (i in 0 until channelsArr.length()) {
                val obj = channelsArr.getJSONObject(i)
                channels.add(RadioChannel(
                    number = obj.getInt("number"),
                    name = obj.optString("name", ""),
                    rxFrequency = obj.getDouble("rxFrequency"),
                    txFrequency = obj.getDouble("txFrequency"),
                    rxTone = jsonToTone(obj.optString("rxTone", "None")),
                    txTone = jsonToTone(obj.optString("txTone", "None")),
                    power = try { PowerLevel.valueOf(obj.getString("power")) } catch (_: Exception) { PowerLevel.HIGH },
                    wideband = obj.optBoolean("wideband", true),
                    bcl = obj.optBoolean("bcl", false),
                    scan = obj.optBoolean("scan", true),
                    isEmpty = false
                ))
            }

            // Restore groups
            if (root.has("groups")) {
                val groupsArr = root.getJSONArray("groups")
                val groups = mutableListOf<ChannelGroup>()
                for (i in 0 until groupsArr.length()) {
                    val obj = groupsArr.getJSONObject(i)
                    val chArr = obj.getJSONArray("channels")
                    groups.add(ChannelGroup(
                        id = "group_${System.currentTimeMillis()}_$i",
                        name = obj.getString("name"),
                        channelNumbers = (0 until chArr.length()).map { chArr.getInt(it) },
                        color = obj.optInt("color", 0xFF1B5E20.toInt())
                    ))
                }
                saveGroups(groups)
            }

            // Restore favorites
            if (root.has("favorites")) {
                val favsArr = root.getJSONArray("favorites")
                val favs = (0 until favsArr.length()).map { favsArr.getInt(it).toString() }.toSet()
                prefs.edit().putStringSet(KEY_FAVORITES, favs).apply()
            }

            RestoreResult(true, channels, "${channels.size} channels, ${getGroups().size} groups restored")
        } catch (e: Exception) {
            RestoreResult(false, emptyList(), "Restore failed: ${e.message}")
        }
    }

    data class RestoreResult(
        val success: Boolean,
        val channels: List<RadioChannel>,
        val summary: String
    )

    private fun toneToJson(tone: ToneValue): String {
        return when (tone) {
            ToneValue.None -> "None"
            is ToneValue.CTCSS -> "CTCSS:${tone.frequency}"
            is ToneValue.DCS -> "DCS:${tone.code}:${tone.polarity.name}"
        }
    }

    private fun jsonToTone(str: String): ToneValue {
        if (str == "None" || str.isBlank()) return ToneValue.None
        val parts = str.split(":")
        return when (parts[0]) {
            "CTCSS" -> ToneValue.CTCSS(parts[1].toDouble())
            "DCS" -> ToneValue.DCS(
                parts[1].toInt(),
                try { DcsPolarity.valueOf(parts[2]) } catch (_: Exception) { DcsPolarity.NORMAL }
            )
            else -> ToneValue.None
        }
    }

    // --- Diff View ---

    data class ChannelDiff(
        val channelNumber: Int,
        val type: DiffType,
        val radioChannel: RadioChannel?,
        val appChannel: RadioChannel?
    )

    enum class DiffType { ADDED, REMOVED, MODIFIED, UNCHANGED }

    /**
     * Compare two channel lists (e.g., radio contents vs. app configuration).
     * Returns a list of diffs for channels that differ.
     */
    fun diffChannels(radioChannels: List<RadioChannel>, appChannels: List<RadioChannel>): List<ChannelDiff> {
        val diffs = mutableListOf<ChannelDiff>()
        val maxSlots = maxOf(radioChannels.size, appChannels.size)

        for (i in 0 until maxSlots) {
            val radio = radioChannels.getOrNull(i)
            val app = appChannels.getOrNull(i)

            val type = when {
                radio == null && app != null && !app.isEmpty -> DiffType.ADDED
                radio != null && !radio.isEmpty && (app == null || app.isEmpty) -> DiffType.REMOVED
                radio != null && app != null && !radio.isEmpty && !app.isEmpty && radio != app -> DiffType.MODIFIED
                else -> DiffType.UNCHANGED
            }

            if (type != DiffType.UNCHANGED) {
                diffs.add(ChannelDiff(i, type, radio, app))
            }
        }

        return diffs
    }
}
