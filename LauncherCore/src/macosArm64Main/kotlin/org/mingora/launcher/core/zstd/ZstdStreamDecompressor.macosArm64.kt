package org.mingora.launcher.core.zstd

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import org.mingora.launcher.core.exception.ZstdException
import platform.posix.memcpy
import zstd.ZSTD_DCtx
import zstd.ZSTD_DStreamOutSize
import zstd.ZSTD_createDStream
import zstd.ZSTD_decompressStream
import zstd.ZSTD_getErrorName
import zstd.ZSTD_inBuffer
import zstd.ZSTD_initDStream
import zstd.ZSTD_isError
import zstd.ZSTD_outBuffer

@OptIn(markerClass = [ExperimentalForeignApi::class])
internal actual class ZstdStreamDecompressor {
    private val outputChunkSize = ZSTD_DStreamOutSize().toInt()
    private val dstream: CPointer<ZSTD_DCtx> = checkNotNull(ZSTD_createDStream()) {
        "Cannot create Stream Context as null had been returned by `ZSTD_createDStream()`"
    }

    /** 持久化的原生输出缓冲区，避免每次调用都重新分配。 */
    private val outputBuffer: CPointer<ByteVar> = nativeHeap.allocArray<ByteVar>(outputChunkSize)

    private var totalOutputBytes: Long = 0L
    private var closed: Boolean = false
    /** 当前 dstream 是否承接一个尚未完成的 frame。 */
    private var hasActiveFrame: Boolean = false

    init {
        require(outputChunkSize > 0) { "outputChunkSize must granter than 0，actual is $outputChunkSize" }
        initDStream()
    }

    actual fun decompress(
        input: ByteArray,
        offset: Int,
        length: Int
    ): DecompressResult {
        val accumulator = ByteArrayAccumulator()
        val result = decompressChunked(input, offset, length) { accumulator.append(it) }
        return DecompressResult(accumulator.toByteArray(), result.frameComplete, result.totalOutputBytes)
    }

    actual fun decompressChunked(
        input: ByteArray,
        offset: Int,
        length: Int,
        sink: (ByteArray) -> Unit,
    ): DecompressResult {
        // 一个 decompressor 实例会被 DI 作为单例复用。不同 manifest/chunk 通常是彼此
        // 独立的 zstd frame；只有上一次输入没有结束 frame 时，才继续使用旧 dstream。
        if (!hasActiveFrame) {
            initDStream()
        }

        return try {
            val result = decompressTo(input, offset, length, sink)
            hasActiveFrame = !result.frameComplete
            result
        } catch (error: Throwable) {
            // zstd 文档规定发生错误后 dstream 状态未定义，下一次必须 reset。
            hasActiveFrame = false
            throw error
        }
    }

    private fun initDStream() {
        val ret = ZSTD_initDStream(dstream)
        if (ZSTD_isError(ret) != 0u) {
            throw ZstdException("ZSTD_initDStream 失败: ${errorName(ret)}", ret)
        }
    }

    private fun decompressTo(
        input: ByteArray,
        offset: Int,
        length: Int,
        sink: (ByteArray) -> Unit,
    ): DecompressResult {
        ensureOpen()
        require(offset >= 0 && length >= 0 && offset + length <= input.size) {
            "输入区间越界: size=${input.size}, offset=$offset, length=$length"
        }
        if (length == 0) {
            return DecompressResult(ByteArray(0), frameComplete = false, totalOutputBytes)
        }

        var frameComplete = false
        input.usePinned { pinned ->
            memScoped {
                val inBuffer = alloc<ZSTD_inBuffer>()
                inBuffer.src = pinned.addressOf(offset)
                inBuffer.size = length.toULong()
                inBuffer.pos = 0u

                val outBuffer = alloc<ZSTD_outBuffer>()
                outBuffer.dst = outputBuffer
                outBuffer.size = outputChunkSize.toULong()
                outBuffer.pos = 0u

                // 与官方 streaming_decompression 示例一致：把本次输入全部消费掉，
                // 并在输入已经消费完但 zstd 仍有内部输出时继续 flush。只检查
                // input.pos 会漏掉最后一个 block，下一次复用 dstream 时就可能触发
                // checksum 错误。
                var flushPending = false
                while (inBuffer.pos < inBuffer.size || flushPending) {
                    outBuffer.pos = 0u
                    val ret = ZSTD_decompressStream(dstream, outBuffer.ptr, inBuffer.ptr)
                    if (ZSTD_isError(ret) != 0u) {
                        throw ZstdException(
                            "zstd 流式解压失败: ${errorName(ret)} (0x${ret.toString(16)})",
                            ret,
                        )
                    }
                    val produced = outBuffer.pos.toInt()
                    if (produced > 0) {
                        val chunk = ByteArray(produced)
                        memcpy(chunk.refTo(0), outputBuffer, produced.toULong())
                        sink(chunk)
                        totalOutputBytes += produced
                    }
                    if (ret == 0u.toULong()) {
                        frameComplete = true
                        flushPending = false
                    } else if (inBuffer.pos >= inBuffer.size) {
                        // 没有输入、没有输出且 ret 仍大于 0，说明输入是不完整的；
                        // 返回 false 交给上层决定是否继续追加数据，避免空转。
                        flushPending = produced > 0
                    }
                }
            }
        }
        return DecompressResult(ByteArray(0), frameComplete, totalOutputBytes)
    }

    private fun ensureOpen() {
        check(!closed) { "ZstdStreamDecompressor has been close，and cannot be used again." }
    }

    private fun errorName(code: ULong): String =
        ZSTD_getErrorName(code)?.toKString() ?: "unknown error"
}