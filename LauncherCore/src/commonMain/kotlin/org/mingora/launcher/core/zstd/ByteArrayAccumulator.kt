package org.mingora.launcher.core.zstd

internal class ByteArrayAccumulator(initialCapacity: Int = 64) {
    private var buffer: ByteArray = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    fun append(src: ByteArray, length: Int = src.size) {
        if (size + length > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, size + length))
        }
        src.copyInto(buffer, destinationOffset = size, startIndex = 0, endIndex = length)
        size += length
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}