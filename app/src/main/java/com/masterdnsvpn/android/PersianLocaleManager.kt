package com.masterdnsvpn.android

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object PersianLocaleManager {
    private val persianLocale: Locale = Locale.forLanguageTag("fa")

    fun wrap(context: Context): Context {
        Locale.setDefault(persianLocale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(persianLocale)
        configuration.setLayoutDirection(persianLocale)

        return context.createConfigurationContext(configuration)
    }

    fun applyToResources(context: Context) {
        Locale.setDefault(persianLocale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(persianLocale)
        configuration.setLayoutDirection(persianLocale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        }
    }
}
