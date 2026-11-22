package com.nexus.music

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches launcher activity aliases without restarting the current app process. */
object LauncherIconManager {
    private val aliases = listOf(
        "1" to "LauncherIconOne",
        "2" to "LauncherIconTwo",
        "3" to "LauncherIconThree"
    )

    fun current(context: Context): String {
        val packageManager = context.packageManager
        val explicitlyEnabled = aliases.firstOrNull { (_, className) ->
            packageManager.getComponentEnabledSetting(component(context, className)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        return explicitlyEnabled?.first ?: "3"
    }

    fun apply(context: Context, selected: String) {
        val choice = selected.takeIf { value -> aliases.any { it.first == value } } ?: "3"
        val packageManager = context.packageManager
        aliases.forEach { (id, className) ->
            packageManager.setComponentEnabledSetting(
                component(context, className),
                if (id == choice) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun component(context: Context, className: String) =
        ComponentName(context, "${context.packageName}.$className")
}
