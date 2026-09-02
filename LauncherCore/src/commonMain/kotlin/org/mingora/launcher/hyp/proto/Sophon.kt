package org.mingora.launcher.hyp.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Chunk 模式游戏文件清单
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonManifestChunkMode(
    @ProtoNumber(1)
    val chunks: List<SophonFileChunkMode> = emptyList(),
)

// Chunk 模式游戏文件
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonFileChunkMode(
    @ProtoNumber(1)
    val file: String = "",

    @ProtoNumber(2)
    val chunks: List<SophonChunk> = emptyList(),

    @ProtoNumber(3)
    val isFolder: Boolean = false,

    @ProtoNumber(4)
    val size: Long = 0,

    @ProtoNumber(5)
    val md5: String = ""
)

// Chunk 模式游戏文件块
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonChunk(
    @ProtoNumber(1)
    val id: String = "",

    @ProtoNumber(2)
    val uncompressedMd5: String = "",

    @ProtoNumber(3)
    val offset: Long = 0,

    @ProtoNumber(4)
    val compressedSize: Long = 0,

    @ProtoNumber(5)
    val uncompressedSize: Long = 0,

    @ProtoNumber(6)
    val unknown: Long = 0,

    @ProtoNumber(7)
    val compressedMd5: String = ""
)

// Patch 更新模式游戏文件清单
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonManifestPatchMode(
    // 新版本所有文件
    @ProtoNumber(1)
    val patches: List<SophonFilePatchMode> = emptyList(),

    // 需要删除的文件
    @ProtoNumber(2)
    val deleteTags: List<SophonPatchDeleteTag> = emptyList(),

    // 压缩模式
    @ProtoNumber(3)
    val compressMode: Int = 0
)

// Patch 更新模式，新版本游戏文件
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonFilePatchMode(
    @ProtoNumber(1)
    val file: String = "",

    @ProtoNumber(2)
    val size: Long = 0,

    @ProtoNumber(3)
    val md5: String = "",

    // 不同版本的补丁文件，如果为空则表示不需要更新
    @ProtoNumber(4)
    val patches: List<SophonPatchInfo> = emptyList()
)

// Patch 更新模式，游戏文件补丁
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonPatch(
    @ProtoNumber(1)
    val id: String = "",

    @ProtoNumber(2)
    val tag: String = "",

    @ProtoNumber(3)
    val buildId: String = "",

    // 文件大小
    @ProtoNumber(4)
    val patchFileSize: Long = 0,

    @ProtoNumber(5)
    val patchFileMd5: String = "",

    // 当前游戏文件补丁的偏移量
    @ProtoNumber(6)
    val patchOffset: Long = 0,

    // 当前游戏文件补丁的长度
    @ProtoNumber(7)
    val patchLength: Long = 0,

    // 如果存在，表示需要使用 hdiffpatch 更新
    @ProtoNumber(8)
    val originalFileName: String = "",

    @ProtoNumber(9)
    val originalFileSize: Long = 0,

    @ProtoNumber(10)
    val originalFileMd5: String = ""
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonPatchInfo(
    // 更新前游戏版本
    @ProtoNumber(1)
    val tag: String = "",

    @ProtoNumber(2)
    val patch: SophonPatch? = null
)

// Patch 更新模式，需要删除文件
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonPatchDeleteFile(
    @ProtoNumber(1)
    val file: String = "",

    @ProtoNumber(2)
    val size: Long = 0,

    @ProtoNumber(3)
    val md5: String = ""
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonPatchDeleteFileCollection(
    @ProtoNumber(1)
    val deleteFiles: List<SophonPatchDeleteFile> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SophonPatchDeleteTag(
    // 更新前游戏版本
    @ProtoNumber(1)
    val tag: String = "",

    @ProtoNumber(2)
    val deleteCollection: SophonPatchDeleteFileCollection? = null
)