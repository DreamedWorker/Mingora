package org.mingora.launcher.core.util

internal expect object DeviceUtil {
    fun getCpuCoreCount(): Int
}