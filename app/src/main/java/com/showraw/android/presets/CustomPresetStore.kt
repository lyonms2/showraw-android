package com.showraw.android.presets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
        put("eqLowGainDb",          eqLowGainDb)
        put("eqMidGainDb",          eqMidGainDb)
        put("eqHighGainDb",         eqHighGainDb)
        put("stabilization",        stabilization)
        put("inputGainDb",          inputGainDb)
        put("compressorThreshold",  compressorThreshold)
        put("compressorRatio",      compressorRatio)
        put("compressorAttack",     compressorAttack)
        put("compressorRelease",    compressorRelease)
        put("compressorMakeupDb",   compressorMakeupDb)
        put("maxDurationMinutes",   maxDurationMinutes)
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
            eqLowGainDb         = getDouble("eqLowGainDb").toFloat(),
            eqMidGainDb         = getDouble("eqMidGainDb").toFloat(),
            eqHighGainDb        = getDouble("eqHighGainDb").toFloat(),
            stabilization       = getBoolean("stabilization"),
            inputGainDb         = getDouble("inputGainDb").toFloat(),
            compressorThreshold = getDouble("compressorThreshold").toFloat(),
            compressorRatio     = getDouble("compressorRatio").toFloat(),
            compressorAttack    = getDouble("compressorAttack").toFloat(),
            compressorRelease   = getDouble("compressorRelease").toFloat(),
            compressorMakeupDb  = getDouble("compressorMakeupDb").toFloat(),
            maxDurationMinutes  = getInt("maxDurationMinutes"),
        )
    }
}
