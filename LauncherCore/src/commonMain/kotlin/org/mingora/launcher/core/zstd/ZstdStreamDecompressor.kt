package org.mingora.launcher.core.zstd

internal expect class ZstdStreamDecompressor() {
    /**
     * 解压一段输入，返回本次调用解出的全部字节。
     *
     * 输入会被完整消费（内部循环直到 `input.pos == input.size`）；若输入不足以解完当前
     * frame，返回的 [DecompressResult.frameComplete] 为 false，调用方应继续进行下一块。
     *
     * @param input 压缩数据。
     * @param offset 本块数据在 [input] 中的起始下标，默认 0。
     * @param length 本块数据的长度，默认 [input] 的剩余长度。
     * @throws org.mingora.launcher.core.exception.ZstdException 输入不是合法的 zstd 流（或帧损坏）时。
     */
    fun decompress(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
    ): DecompressResult


    /**
     * 流式解压输入，并将输出按小块交给 [sink]，避免为了返回完整结果而分配一个
     * 与解压后数据等大的 ByteArray。
     */
    fun decompressChunked(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
        sink: (ByteArray) -> Unit,
    ): DecompressResult
}