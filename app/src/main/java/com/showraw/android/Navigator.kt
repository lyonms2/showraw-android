package com.showraw.android

interface Navigator {
    fun startRecording(presetId: String)
    fun showExport(videoPath: String, wavPath: String, sessionName: String = "")
    fun newRecording()
    fun showProSettings()
    fun showLibrary()
    fun showManual()
    fun showCustomPresetEditor(presetId: String? = null)
}
