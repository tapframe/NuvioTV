package com.nuvio.tv.data.parser

import com.nuvio.tv.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Lightweight EBML parser that extracts MKV chapter data via HTTP range requests.
 * Reads the first 2MB of the file to locate the Chapters element. If Chapters
 * are not in the first 2MB, uses SeekHead to locate them and makes a targeted
 * second range request.
 */
object MatroskaChapterExtractor {

    // EBML element IDs
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_SEEK_HEAD = 0x114D9B74L
    private const val ID_SEEK = 0x4DBB
    private const val ID_SEEK_ID = 0x53AB
    private const val ID_SEEK_POSITION = 0x53AC
    private const val ID_CHAPTERS = 0x1043A770L
    private const val ID_EDITION_ENTRY = 0x45B9
    private const val ID_CHAPTER_ATOM = 0xB6
    private const val ID_CHAPTER_TIME_START = 0x91
    private const val ID_CHAPTER_TIME_END = 0x92
    private const val ID_CHAPTER_DISPLAY = 0x80
    private const val ID_CHAP_STRING = 0x85
    private const val ID_CHAPTER_FLAG_HIDDEN = 0x98
    private const val ID_CHAPTER_FLAG_ENABLED = 0x4598
    private const val ID_CLUSTER = 0x1F43B675L

    private const val INITIAL_RANGE_SIZE = 2 * 1024 * 1024 // 2MB
    private const val CHAPTER_FETCH_SIZE = 512 * 1024 // 512KB for targeted chapter fetch

    suspend fun extractChapters(
        url: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient
    ): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val initialData = fetchRange(url, headers, okHttpClient, 0L, INITIAL_RANGE_SIZE.toLong() - 1)
                ?: return@withContext emptyList()

            val stream = ByteArrayInputStream(initialData)

            // Parse EBML header first
            val ebmlHeader = readElement(stream) ?: return@withContext emptyList()
            // Skip EBML header content
            skipBytes(stream, ebmlHeader.dataSize)

            // Expect Segment element
            val segment = readElement(stream) ?: return@withContext emptyList()
            if (segment.id != ID_SEGMENT) return@withContext emptyList()

            val segmentDataStart = initialData.size.toLong() - stream.available().toLong()

            // Parse within Segment looking for SeekHead and Chapters
            var seekHeadChaptersOffset: Long? = null
            var chapters: List<Chapter>? = null

            while (stream.available() > 0) {
                val element = readElement(stream) ?: break
                val elementDataStart = initialData.size.toLong() - stream.available().toLong()

                when (element.id) {
                    ID_SEEK_HEAD -> {
                        val seekHeadData = readBytes(stream, element.dataSize.toInt()) ?: break
                        seekHeadChaptersOffset = parseSeekHead(seekHeadData)
                    }
                    ID_CHAPTERS -> {
                        val chaptersData = readBytes(stream, element.dataSize.toInt()) ?: break
                        chapters = parseChaptersElement(chaptersData)
                    }
                    ID_CLUSTER -> {
                        // Stop scanning once we hit media data
                        break
                    }
                    else -> {
                        skipBytes(stream, element.dataSize)
                    }
                }

                if (chapters != null) break
            }

            if (chapters != null) return@withContext chapters

            // Chapters not in initial range - use SeekHead offset for a second request
            if (seekHeadChaptersOffset != null) {
                val absoluteOffset = segmentDataStart + seekHeadChaptersOffset
                val chaptersData = fetchRange(
                    url, headers, okHttpClient,
                    absoluteOffset.toLong(),
                    (absoluteOffset + CHAPTER_FETCH_SIZE - 1).toLong()
                ) ?: return@withContext emptyList()

                val chapStream = ByteArrayInputStream(chaptersData)
                val chapElement = readElement(chapStream) ?: return@withContext emptyList()
                if (chapElement.id != ID_CHAPTERS) return@withContext emptyList()

                val chapContent = readBytes(chapStream, chapElement.dataSize.toInt())
                    ?: return@withContext emptyList()
                return@withContext parseChaptersElement(chapContent)
            }

            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchRange(
        url: String,
        headers: Map<String, String>,
        client: OkHttpClient,
        start: Long,
        end: Long
    ): ByteArray? {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        val response = client.newCall(requestBuilder.build()).execute()
        return response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) return@use null
            resp.body?.bytes()
        }
    }

    /**
     * Parse SeekHead to find the byte offset of the Chapters element relative to Segment data start.
     */
    private fun parseSeekHead(data: ByteArray): Long? {
        val stream = ByteArrayInputStream(data)
        while (stream.available() > 0) {
            val element = readElement(stream) ?: break
            if (element.id.toLong() == ID_SEEK.toLong()) {
                val seekData = readBytes(stream, element.dataSize.toInt()) ?: break
                val result = parseSeekEntry(seekData)
                if (result != null) return result
            } else {
                skipBytes(stream, element.dataSize)
            }
        }
        return null
    }

    /**
     * Parse a single Seek entry. Returns the position if this entry points to Chapters.
     */
    private fun parseSeekEntry(data: ByteArray): Long? {
        val stream = ByteArrayInputStream(data)
        var seekId: Long? = null
        var seekPosition: Long? = null

        while (stream.available() > 0) {
            val element = readElement(stream) ?: break
            when (element.id.toLong()) {
                ID_SEEK_ID.toLong() -> {
                    val idBytes = readBytes(stream, element.dataSize.toInt()) ?: break
                    seekId = readVarLong(idBytes)
                }
                ID_SEEK_POSITION.toLong() -> {
                    val posBytes = readBytes(stream, element.dataSize.toInt()) ?: break
                    seekPosition = readUnsignedInt(posBytes)
                }
                else -> skipBytes(stream, element.dataSize)
            }
        }

        return if (seekId == ID_CHAPTERS) seekPosition else null
    }

    private val GENERIC_CHAPTER_PATTERN = Regex("^Chapter\\s+\\d+$", RegexOption.IGNORE_CASE)

    private fun parseChaptersElement(data: ByteArray): List<Chapter> {
        val stream = ByteArrayInputStream(data)
        val allChapters = mutableListOf<Chapter>()

        while (stream.available() > 0) {
            val element = readElement(stream) ?: break
            if (element.id.toLong() == ID_EDITION_ENTRY.toLong()) {
                val editionData = readBytes(stream, element.dataSize.toInt()) ?: break
                allChapters.addAll(parseEditionEntry(editionData))
            } else {
                skipBytes(stream, element.dataSize)
            }
        }

        // Merge chapters from all editions: deduplicate by timestamp proximity
        // (within 1s), preferring descriptive titles over generic "Chapter XX".
        return mergeChapters(allChapters)
    }

    private fun mergeChapters(chapters: List<Chapter>): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()

        val sorted = chapters.sortedBy { it.startTimeMs }
        val merged = mutableListOf(sorted.first())

        for (ch in sorted.drop(1)) {
            val last = merged.last()
            if (kotlin.math.abs(ch.startTimeMs - last.startTimeMs) <= 1000) {
                // Same chapter position — keep the one with the more descriptive title
                if (isDescriptiveTitle(ch.title) && !isDescriptiveTitle(last.title)) {
                    merged[merged.lastIndex] = ch
                }
            } else {
                merged.add(ch)
            }
        }

        return merged
    }

    private fun isDescriptiveTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        return !GENERIC_CHAPTER_PATTERN.matches(title)
    }

    private fun parseEditionEntry(data: ByteArray): List<Chapter> {
        val stream = ByteArrayInputStream(data)
        val chapters = mutableListOf<Chapter>()

        while (stream.available() > 0) {
            val element = readElement(stream) ?: break
            if (element.id.toLong() == ID_CHAPTER_ATOM.toLong()) {
                val atomData = readBytes(stream, element.dataSize.toInt()) ?: break
                parseChapterAtom(atomData)?.let { chapters.add(it) }
            } else {
                skipBytes(stream, element.dataSize)
            }
        }

        return chapters
    }

    private fun parseChapterAtom(data: ByteArray): Chapter? {
        val stream = ByteArrayInputStream(data)
        var startTimeNs: Long? = null
        var endTimeNs: Long? = null
        var title: String? = null
        var hidden = false
        var enabled = true

        while (stream.available() > 0) {
            val element = readElement(stream) ?: break
            when (element.id.toLong()) {
                ID_CHAPTER_TIME_START.toLong() -> {
                    val bytes = readBytes(stream, element.dataSize.toInt()) ?: break
                    startTimeNs = readUnsignedInt(bytes)
                }
                ID_CHAPTER_TIME_END.toLong() -> {
                    val bytes = readBytes(stream, element.dataSize.toInt()) ?: break
                    endTimeNs = readUnsignedInt(bytes)
                }
                ID_CHAPTER_DISPLAY.toLong() -> {
                    val displayData = readBytes(stream, element.dataSize.toInt()) ?: break
                    title = title ?: parseChapterDisplay(displayData)
                }
                ID_CHAPTER_FLAG_HIDDEN.toLong() -> {
                    val bytes = readBytes(stream, element.dataSize.toInt()) ?: break
                    hidden = readUnsignedInt(bytes) == 1L
                }
                ID_CHAPTER_FLAG_ENABLED.toLong() -> {
                    val bytes = readBytes(stream, element.dataSize.toInt()) ?: break
                    enabled = readUnsignedInt(bytes) != 0L
                }
                else -> skipBytes(stream, element.dataSize)
            }
        }

        if (hidden || !enabled) return null
        val startMs = (startTimeNs ?: return null) / 1_000_000
        val endMs = (endTimeNs ?: 0L) / 1_000_000

        return Chapter(
            title = title,
            startTimeMs = startMs,
            endTimeMs = endMs
        )
    }

    private fun parseChapterDisplay(data: ByteArray): String? {
        val stream = ByteArrayInputStream(data)
        while (stream.available() > 0) {
            val element = readElement(stream) ?: break
            if (element.id.toLong() == ID_CHAP_STRING.toLong()) {
                val bytes = readBytes(stream, element.dataSize.toInt()) ?: break
                return String(bytes, Charsets.UTF_8)
            } else {
                skipBytes(stream, element.dataSize)
            }
        }
        return null
    }

    // --- EBML primitives ---

    private data class EbmlElement(val id: Long, val dataSize: Long)

    private fun readElement(stream: InputStream): EbmlElement? {
        val id = readVINT(stream, idMode = true) ?: return null
        val size = readVINT(stream, idMode = false) ?: return null
        return EbmlElement(id, size)
    }

    /**
     * Read a variable-length integer from the stream.
     * In id mode, the VINT_MARKER bit is kept as part of the value.
     * In size mode, the VINT_MARKER bit is masked off.
     */
    private fun readVINT(stream: InputStream, idMode: Boolean): Long? {
        val first = stream.read()
        if (first == -1) return null

        val length = when {
            first and 0x80 != 0 -> 1
            first and 0x40 != 0 -> 2
            first and 0x20 != 0 -> 3
            first and 0x10 != 0 -> 4
            first and 0x08 != 0 -> 5
            first and 0x04 != 0 -> 6
            first and 0x02 != 0 -> 7
            first and 0x01 != 0 -> 8
            else -> return null
        }

        var value = if (idMode) {
            first.toLong()
        } else {
            // Mask off the leading 1-bit
            (first and ((1 shl (8 - length)) - 1)).toLong()
        }

        for (i in 1 until length) {
            val b = stream.read()
            if (b == -1) return null
            value = (value shl 8) or b.toLong()
        }

        return value
    }

    private fun readBytes(stream: InputStream, count: Int): ByteArray? {
        if (count <= 0) return ByteArray(0)
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = stream.read(buf, offset, count - offset)
            if (read == -1) return null
            offset += read
        }
        return buf
    }

    private fun skipBytes(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                // Try reading one byte at a time as fallback
                if (stream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readUnsignedInt(bytes: ByteArray): Long {
        var value = 0L
        for (b in bytes) {
            value = (value shl 8) or (b.toLong() and 0xFF)
        }
        return value
    }

    private fun readVarLong(bytes: ByteArray): Long {
        var value = 0L
        for (b in bytes) {
            value = (value shl 8) or (b.toLong() and 0xFF)
        }
        return value
    }
}
