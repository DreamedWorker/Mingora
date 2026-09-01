package org.mingora.launcher.core.exception

class ZstdException(
    message: String,
    val errorCode: ULong,
) : RuntimeException(message)