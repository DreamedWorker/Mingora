package org.mingora.launcher.core.util

import platform.Foundation.NSProcessInfo

internal actual object DeviceUtil {
    actual fun getCpuCoreCount(): Int {
        return NSProcessInfo.processInfo.processorCount.toInt()
    }
}
