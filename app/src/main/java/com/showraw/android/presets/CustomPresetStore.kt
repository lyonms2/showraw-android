package com.showraw.android.presets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object CustomPresetStore {

    private const val PREFS = "custom_presets"
    private const val KEY   = "json"

    fun load(context: Context): List<Preset> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).toPreset() }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, preset: Preset) {
        val list = load(context).toMutableList()
        val idx  = list.indexOfFirst { it.id == preset.id }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        persist(context, list)
    }

    fun delete(context: Context, id: String) {
        persist(context, load(context).filter { it.id != id })
    }

    fun toExportJson(preset: Preset): String = preset.toJson().toString()

    fun importFromJson(context: Context, json: String): Boolean = runCatching {
        val imported = JSONObject(json).toPreset().copy(id = UUID.randomUUID().toString())
        save(context, imported)
        true
    }.getOrDefault(false)

    private fun persist(context: Context, list: List<Preset>) {
        val arr = JSONArray(list.map { it.toJson() })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun Preset.toJson() = JSONObject().apply {
        put("id",                   id)
        put("name",                 name)
        put("emoji",                emoji)
        put("description",          description)
        put("limiterThreshold",     limiterThreshold)
        put("limiterAttack",        limiterAttack)
        put("limiterRelease",       limiterRelease)
        put("hpfFrequency",         hpfFrequency)
        put("hpfRolloff",           hpfRolloff)
        put("videoResolution",      videoResolution.name)
        put("estimatedMbPerMin",    estimatedMbPerMin)
        put("noiseGateThreshold",   noiseGateThreshold)
        put("eqBands",              JSONArray(eqBands.map { it.toJson() }))
        put("stabilization",        stabilization)
        put("inputGainDb",          inputGainDb)
        put("compressorThreshold",  compressorThreshold)
        put("compressorRatio",      compressorRatio)
        put("compressorAttack",     compressorAttack)
        put("compressorRelease",    compressorRelease)
        put("compressorMakeupDb",   compressorMakeupDb)
        put("compressorEnabled",    compressorEnabled)
        put("maxDurationMinutes",   maxDurationMinutes)
    }

    private fun EqBand.toJson() = JSONObject().apply {
        put("type",   type.name)
        put("freq",   freq)
        put("gainDb", gainDb)
        put("q",      q)
    }

    private fun JSONObject.toPreset(): Preset {
        val res = Resolution.valueOf(getString("videoResolution"))
        return Preset(
            id                  = getString("id"),
            name                = getString("name"),
            emoji               = getString("emoji"),
            description         = getString("description"),
            limiterThreshold    = getDouble("limiterThreshold").toFloat(),
            limiterAttack       = getDouble("limiterAttack").toFloat(),
            limiterRelease      = getDouble("limiterRelease").toFloat(),
            hpfFrequency        = getDouble("hpfFrequency").toFloat(),
            hpfRolloff          = getInt("hpfRolloff"),
            videoResolution     = res,
            estimatedMbPerMin   = getInt("estimatedMbPerMin"),
            contextualWarning   = null,
            noiseGateThreshold  = getDouble("noiseGateThreshold").toFloat(),
            eqBands             = run {
                val arr = optJSONArray("eqBands")
                if (arr != null) (0 until arr.length()).map { arr.getJSONObject(it).toEqBand() }
                else defaultEqBands()
            },
            stabilization       = getBoolean("stabilization"),
            inputGainDb         = getDouble("inputGainDb").toFloat(),
            compressorThreshold = getDouble("compressorThreshold").toFloat(),
            compressorRatio     = getDouble("compressorRatio").toFloat(),
            compressorAttack    = getDouble("compressorAttack").toFloat(),
            compressorRelease   = getDouble("compressorRelease").toFloat(),
            compressorMakeupDb  = getDouble("compressorMakeupDb").toFloat(),
            compressorEnabled   = optBoolean("compressorEnabled", true),
            maxDurationMinutes  = getInt("maxDurationMinutes"),
        )
    }

    private fun JSONObject.toEqBand() = EqBand(
        type   = EqFilterType.valueOf(getString("type")),
        freq   = getDouble("freq").toFloat(),
        gainDb = getDouble("gainDb").toFloat(),
        q      = getDouble("q").toFloat(),
    )
}
