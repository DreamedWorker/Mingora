package org.mingora.launcher.core.zstd

/**
 * 一次 [ZstdStreamDecompressor.decompress] / [ZstdStreamDecompressor.decompressChunked] 调用的结果。
 *
 * @property output 本次调用解压出的字节（[ZstdStreamDecompressor.decompressChunked] 时为
 *   空数组，数据通过 sink 回调逐块送出）。
 * @property frameComplete 本次调用结束时是否恰好位于某个 zstd frame 的解码完成点
 *   （底层 [zstd.ZSTD_decompressStream] 返回 0）。
 *   - 单 frame 流的最后一次调用通常为 true；
 *   - 多 frame 拼接流中，若本次调用恰好解完一个 frame 后还有后续输入，该值反映的是
 *     最后被处理的 frame 的状态。
 * @property totalOutputBytes 该解压器自创建（或上次 [ZstdStreamDecompressor.reset]）以来
 *   累计解压出的字节数。
 */
internal class DecompressResult(
    val output: ByteArray,
    val frameComplete: Boolean,
    val totalOutputBytes: Long,
)