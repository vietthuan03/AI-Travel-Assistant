package com.example.travelassistant.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

fun Fragment.openExternalLink(url: String) {
    if (!isResumed) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Snackbar.make(requireView(), R.string.external_link_unavailable, Snackbar.LENGTH_LONG).show()
    }
}
