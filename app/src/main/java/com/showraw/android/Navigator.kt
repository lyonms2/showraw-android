package com.showraw.android

interface Navigator {
    fun startRecording(presetId: String)
    fun showExport(videoPath: String, wavPath: String)
    fun newRecording()
    fun showProSettings()
}
