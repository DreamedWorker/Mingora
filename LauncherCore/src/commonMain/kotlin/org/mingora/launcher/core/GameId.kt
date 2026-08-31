package org.mingora.launcher.core

enum class GameId(val id: String, val biz: String, val launcher: HYPLauncherId) {
    BH3_CN("osvnlOc0S8", "bh3_cn", HYPLauncherId.CHINA_OFFICIAL),
    BH3_GLOBAL("5TIVvvcwtM", "bh3_global", HYPLauncherId.GLOBAL_OFFICIAL),
    HK4E_CN("1Z8W5NHUQb", "hk4e_cn", HYPLauncherId.CHINA_OFFICIAL),
    HK4E_GLOBAL("gopR6Cufr3", "hk4e_global", HYPLauncherId.GLOBAL_OFFICIAL),
    HK4E_BILIBILI("T2S0Gz4Dr2", "hk4e_bilibili", HYPLauncherId.BILIBILI_GENSHIN),
    HKRPG_CN("64kMb5iAWu", "hkrpg_cn", HYPLauncherId.CHINA_OFFICIAL),
    HKRPG_GLOBAL("4ziysqXOQ8", "hkrpg_global", HYPLauncherId.GLOBAL_OFFICIAL),
    HKRPG_BILIBILI("EdtUqXfCHh", "hkrpg_bilibili", HYPLauncherId.BILIBILI_HSR),
    NAP_CN("x6znKlJ0xK", "nap_cn", HYPLauncherId.CHINA_OFFICIAL),
    NAP_GLOBAL("U5hbdsT9W7", "nap_global", HYPLauncherId.GLOBAL_OFFICIAL),
    NAP_BILIBILI("HXAFlmYa17", "nap_bilibili", HYPLauncherId.BILIBILI_NAP);

    companion object {
        fun GameId.isBilibiliServer(): Boolean = when (this) {
            HK4E_BILIBILI, HKRPG_BILIBILI, NAP_BILIBILI -> true
            else -> false
        }

        fun getGameIdsByLauncher(launcher: HYPLauncherId): List<GameId> {
            return when (launcher) {
                HYPLauncherId.CHINA_OFFICIAL -> listOf(BH3_CN, HK4E_CN, HKRPG_CN, NAP_CN)
                HYPLauncherId.GLOBAL_OFFICIAL -> listOf(BH3_GLOBAL, HK4E_GLOBAL, NAP_GLOBAL)
                else -> throw IllegalArgumentException("Unsupported launcher id in this operation: $launcher")
            }
        }

        fun isCNServer(id: String): Boolean = when (id) {
            "osvnlOc0S8", "1Z8W5NHUQb", "64kMb5iAWu", "x6znKlJ0xK" -> true
            else -> false
        }

        fun isOSServer(id: String): Boolean = when (id) {
            "5TIVvvcwtM", "gopR6Cufr3", "4ziysqXOQ8", "U5hbdsT9W7" -> true
            else -> false
        }

        fun convertId(id: String): GameId {
            return entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown id: $id")
        }
    }
}