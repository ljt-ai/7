package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.exception.ParseException
import com.hippo.ehviewer.client.parseAs
import java.nio.ByteBuffer
import kotlinx.serialization.Serializable
import okio.buffer

object TorrentParser {
    fun parse(body: ByteBuffer) = runCatching {
        parseTorrent(body).also {
            body.clear()
            body.limit(it)
        }
        body.asSource().buffer().use { it.parseAs<TorrentResult>() }
    }.getOrElse {
        throw ParseException("Can't parse torrent list", it)
    }
}

@Serializable
data class Torrent(
    val posted: String,
    val size: String,
    val seeds: Int,
    val peers: Int,
    val downloads: Int,
    val url: String,
    val name: String,
)

fun Torrent.format() = "[$posted] $name [$size] [↑$seeds ↓$peers ✓$downloads]"

typealias TorrentResult = List<Torrent>

private external fun parseTorrent(body: ByteBuffer, size: Int = body.limit()): Int
