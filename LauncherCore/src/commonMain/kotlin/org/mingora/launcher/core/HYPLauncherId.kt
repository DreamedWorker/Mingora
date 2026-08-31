package org.mingora.launcher.core

enum class HYPLauncherId(val launcherId: String) {
    CHINA_OFFICIAL("jGHBHlcOq1"),
    GLOBAL_OFFICIAL("VYTpXlbWo8"),
    BILIBILI_GENSHIN("umfgRO5gh5"),
    BILIBILI_HSR("6P5gHMNyK3"),
    BILIBILI_NAP("xV0f4r1GT0");

    companion object {
        fun HYPLauncherId.isBiliLauncher(): Boolean = when(this) {
            BILIBILI_GENSHIN, BILIBILI_HSR, BILIBILI_NAP -> true
            else -> false
        }

        fun convert(id: String): HYPLauncherId = when(id) {
            "jGHBHlcOq1" -> CHINA_OFFICIAL
            "VYTpXlbWo8" -> GLOBAL_OFFICIAL
            "umfgRO5gh5" -> BILIBILI_GENSHIN
            "6P5gHMNyK3" -> BILIBILI_HSR
            "xV0f4r1GT0" -> BILIBILI_NAP
            else -> error("no such launcher $id")
        }
    }
}