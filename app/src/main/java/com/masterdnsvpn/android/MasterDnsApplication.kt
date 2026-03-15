package com.masterdnsvpn.android

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MasterDnsApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(PersianLocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()

        PersianLocaleManager.applyToResources(this)

        val persianLocales = LocaleListCompat.forLanguageTags("fa")
        if (AppCompatDelegate.getApplicationLocales() != persianLocales) {
            AppCompatDelegate.setApplicationLocales(persianLocales)
        }
    }
}
