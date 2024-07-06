package id.reinhart1010.altia.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import id.reinhart1010.altia.R


class AppPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.app_preference_fragment, rootKey)
    }
}