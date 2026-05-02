package com.showraw.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.showraw.android.ui.export.ExportFragment
import com.showraw.android.ui.presets.PresetsFragment
import com.showraw.android.ui.recording.RecordingFragment

class MainActivity : AppCompatActivity(), Navigator {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PresetsFragment())
                .commit()
        }
    }

    override fun startRecording(presetId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RecordingFragment.newInstance(presetId))
            .addToBackStack("recording")
            .commit()
    }

    override fun showExport(videoPath: String, wavPath: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ExportFragment.newInstance(videoPath, wavPath))
            .addToBackStack("export")
            .commit()
    }

    override fun newRecording() {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
}
