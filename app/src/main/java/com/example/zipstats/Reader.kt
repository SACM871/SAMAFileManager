package com.example.zipstats

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class Reader(private val contentResolver: ContentResolver) {
    fun read(uri: Uri): Node {
        val header = ByteArray(4)
        contentResolver.openInputStream(uri)?.use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count <= 0) break
                offset += count
            }
        } ?: throw IOException("تعذر فتح الملف")

        return if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            zipTree(uri)
        } else {
            jsonTree(uri)
        }
    }

    private val nameKeys = listOf("name", "server_filename", "filename", "file_name", "title", "folder_name", "path")
    private val childKeys = listOf("children", "items", "contents", "files", "folders", "entries", "list")
    private val sizeKeys = listOf("size", "file_size", "bytes", "length")

    private fun nameOf(obj: JSONObject): String? {
        for (key in nameKeys) {
            val value = obj.opt(key)
            if (value is String && value.isNotEmpty()) {
                return if (key == "path") value.trimEnd('/').substringAfterLast('/') else value
            }
        }
        return null
    }

    private fun childrenOf(obj: JSONObject): Any? {
        for (key in childKeys) {
            val value = obj.opt(key)
            if (value is JSONArray || value is JSONObject) return value
        }
        return null
    }

    private fun sizeOf(obj: JSONObject): Long {
        for (key in sizeKeys) {
            val value = obj.opt(key)
            val parsed = when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            if (parsed != null) return parsed.coerceAtLeast(0L)
        }
        return 0L
    }

    private fun isDirectory(obj: JSONObject): Boolean? {
        for (key in listOf("isdir", "is_dir", "isDir", "is_folder", "dir")) {
            when (val value = obj.opt(key)) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
            }
        }
        return when (obj.optString("type", obj.optString("category", "")).lowercase()) {
            "folder", "directory", "dir" -> true
            "file" -> false
            else -> null
        }
    }

    private fun isFilesystemObject(obj: JSONObject): Boolean =
        nameOf(obj) != null &&
            (childrenOf(obj) != null || isDirectory(obj) != null || sizeKeys.any(obj::has))

    private fun jsonTree(uri: Uri): Node {
        val text = contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_JSON_BYTES) { "ملف JSON أكبر من الحد المدعوم حالياً (256 MB)" }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        } ?: throw IOException("تعذر فتح ملف JSON")

        val cleanText = text.trim().removePrefix("\uFEFF")
        val value = JSONTokener(cleanText).nextValue()
        val root = Node("/", true)

        when (value) {
            is JSONArray -> convertArray(value, root)
            is JSONObject -> {
                if (isFilesystemObject(value)) {
                    val children = childrenOf(value)
                    if (value.optString("name") == "/" && children != null) {
                        when (children) {
                            is JSONArray -> convertArray(children, root)
                            is JSONObject -> convertDictionary(children, root)
                        }
                    } else {
                        convertFilesystemNode(value, root)
                    }
                } else {
                    convertDictionary(value, root)
                }
            }
            else -> throw IOException("صيغة JSON غير مدعومة")
        }

        recalc(root)
        return root
    }

    private fun convertFilesystemNode(obj: JSONObject, parent: Node) {
        val name = nameOf(obj) ?: return
        val children = childrenOf(obj)
        val isDir = isDirectory(obj) ?: (children != null)

        if (isDir) {
            val directory = findOrCreateDirectory(parent, name)
            when (children) {
                is JSONArray -> convertArray(children, directory)
                is JSONObject -> convertDictionary(children, directory)
            }
        } else {
            parent.add(Node(name, false, sizeOf(obj), extOf(name)))
        }
    }

    private fun convertDictionary(obj: JSONObject, directory: Node) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = obj.opt(key)) {
                is JSONObject -> {
                    if (isFilesystemObject(value)) {
                        convertFilesystemNode(value, directory)
                    } else {
                        val child = findOrCreateDirectory(directory, key)
                        convertDictionary(value, child)
                    }
                }
                is JSONArray -> {
                    val child = findOrCreateDirectory(directory, key)
                    convertArray(value, child)
                }
                is Number -> directory.add(Node(key, false, value.toLong().coerceAtLeast(0L), extOf(key)))
                is String -> directory.add(Node(key, false, 0L, extOf(key)))
                else -> Unit
            }
        }
    }

    private fun convertArray(array: JSONArray, directory: Node) {
        if (looksFlat(array)) {
            convertFlatArray(array, directory)
            return
        }

        for (index in 0 until array.length()) {
            when (val value = array.opt(index)) {
                is JSONObject -> {
                    if (isFilesystemObject(value)) {
                        convertFilesystemNode(value, directory)
                    } else {
                        val child = findOrCreateDirectory(directory, "[$index]")
                        convertDictionary(value, child)
                    }
                }
                is JSONArray -> {
                    val child = findOrCreateDirectory(directory, "[$index]")
                    convertArray(value, child)
                }
                is String -> directory.add(Node(value, false, 0L, extOf(value)))
                is Number -> directory.add(Node("[$index]", false, value.toLong().coerceAtLeast(0L), ""))
            }
        }
    }

    private fun looksFlat(array: JSONArray): Boolean {
        if (array.length() == 0) return false
        var checked = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: return false
            val path = obj.optString("path", "")
            if (!path.startsWith("/") || childrenOf(obj) != null) return false
            checked++
        }
        return checked == array.length()
    }

    private fun convertFlatArray(array: JSONArray, base: Node) {
        val directoryIndex = HashMap<String, Node>()

        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val segments = obj.optString("path", "").split('/').filter(String::isNotEmpty)
            if (segments.isEmpty()) continue

            val isDir = isDirectory(obj) ?: false
            var current = base
            var accumulatedPath = ""

            for ((segmentIndex, segment) in segments.withIndex()) {
                val last = segmentIndex == segments.lastIndex
                if (last && !isDir) {
                    if (current.kids.none { !it.isDir && it.name.equals(segment, true) }) {
                        current.add(Node(segment, false, sizeOf(obj), extOf(segment)))
                    }
                } else {
                    accumulatedPath += "/$segment"
                    current = directoryIndex[accumulatedPath] ?: run {
                        val created = Node(segment, true)
                        current.add(created)
                        directoryIndex[accumulatedPath] = created
                        created
                    }
                }
            }
        }
    }

    private fun findOrCreateDirectory(parent: Node, name: String): Node {
        return parent.kids.firstOrNull { it.isDir && it.name.equals(name, true) }
            ?: Node(name, true).also(parent::add)
    }

    // ZIP central directory parser. It reads metadata only; file contents are not extracted.
    private fun readAt(channel: FileChannel, position: Long, byteCount: Int): ByteBuffer {
        require(position >= 0 && byteCount >= 0 && position <= channel.size() - byteCount) {
            "موضع قراءة ZIP خارج حدود الملف"
        }
        val buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 0
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, position + offset)
            if (count < 0) throw IOException("ملف ZIP غير مكتمل")
            if (count == 0) continue
            offset += count
        }
        buffer.flip()
        return buffer
    }

    private fun zipTree(uri: Uri): Node {
        val maxUnsignedInt = 0xFFFFFFFFL
        val root = Node("/", true)
        val directoryIndex = HashMap<String, Node>()

        val descriptor = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("تعذر فتح ملف ZIP")
        descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val length = channel.size()
                if (length < 22) throw IOException("ملف ZIP غير صالح")

                val tailLength = minOf(length, 70_000L).toInt()
                val tail = readAt(channel, length - tailLength, tailLength)
                var eocdOffset = tail.limit() - 22
                while (eocdOffset >= 0 && tail.getInt(eocdOffset) != 0x06054b50) eocdOffset--
                if (eocdOffset < 0) throw IOException("لم يتم العثور على فهرس ZIP")

                var centralDirectorySize = tail.getInt(eocdOffset + 12).toLong() and maxUnsignedInt
                var centralDirectoryOffset = tail.getInt(eocdOffset + 16).toLong() and maxUnsignedInt

                if (centralDirectorySize == maxUnsignedInt || centralDirectoryOffset == maxUnsignedInt ||
                    (tail.getShort(eocdOffset + 10).toInt() and 0xFFFF) == 0xFFFF
                ) {
                    val eocdAbsolute = length - tailLength + eocdOffset
                    if (eocdAbsolute < 20) throw IOException("ZIP64 locator غير موجود")
                    val locator = readAt(channel, eocdAbsolute - 20, 20)
                    if (locator.getInt(0) != 0x07064b50) throw IOException("ZIP64 locator غير صالح")
                    val zip64Offset = locator.getLong(8)
                    val zip64 = readAt(channel, zip64Offset, 56)
                    if (zip64.getInt(0) != 0x06064b50) throw IOException("ZIP64 end record غير صالح")
                    centralDirectorySize = zip64.getLong(40)
                    centralDirectoryOffset = zip64.getLong(48)
                }

                if (centralDirectorySize < 0 || centralDirectorySize > MAX_CENTRAL_DIRECTORY_BYTES ||
                    centralDirectoryOffset < 0 || centralDirectoryOffset + centralDirectorySize > length
                ) {
                    throw IOException("حجم فهرس ZIP خارج الحدود المدعومة")
                }

                val directory = readAt(channel, centralDirectoryOffset, centralDirectorySize.toInt())
                var offset = 0

                while (offset + 46 <= directory.limit()) {
                    if (directory.getInt(offset) != 0x02014b50) break

                    var uncompressedSize = directory.getInt(offset + 24).toLong() and maxUnsignedInt
                    val nameLength = directory.getShort(offset + 28).toInt() and 0xFFFF
                    val extraLength = directory.getShort(offset + 30).toInt() and 0xFFFF
                    val commentLength = directory.getShort(offset + 32).toInt() and 0xFFFF
                    val entryEnd = offset + 46 + nameLength + extraLength + commentLength
                    if (entryEnd > directory.limit()) throw IOException("مدخل ZIP غير مكتمل")

                    val nameBytes = ByteArray(nameLength)
                    val nameBuffer = directory.duplicate()
                    nameBuffer.position(offset + 46)
                    nameBuffer.get(nameBytes)
                    val utf8Flag = (directory.getShort(offset + 8).toInt() and 0x0800) != 0
                    val charset = if (utf8Flag) Charsets.UTF_8 else CharsetForZip.cp437
                    val entryName = String(nameBytes, charset)

                    if (uncompressedSize == maxUnsignedInt) {
                        var extraOffset = offset + 46 + nameLength
                        val extraEnd = extraOffset + extraLength
                        while (extraOffset + 4 <= extraEnd) {
                            val fieldId = directory.getShort(extraOffset).toInt() and 0xFFFF
                            val fieldSize = directory.getShort(extraOffset + 2).toInt() and 0xFFFF
                            if (fieldId == 1 && extraOffset + 4 + fieldSize <= extraEnd && fieldSize >= 8) {
                                uncompressedSize = directory.getLong(extraOffset + 4)
                                break
                            }
                            extraOffset += 4 + fieldSize
                        }
                    }

                    addZipEntry(root, directoryIndex, entryName, uncompressedSize.coerceAtLeast(0L))
                    offset = entryEnd
                }
            }
        }

        recalc(root)
        return root
    }

    private fun addZipEntry(
        root: Node,
        directoryIndex: MutableMap<String, Node>,
        entryName: String,
        size: Long
    ) {
        val isDirectory = entryName.endsWith("/")
        val parts = entryName.trimEnd('/').split('/').filter(String::isNotEmpty)
        if (parts.isEmpty()) return

        var current = root
        var accumulated = ""
        for ((index, part) in parts.withIndex()) {
            val last = index == parts.lastIndex
            if (last && !isDirectory) {
                if (current.kids.none { !it.isDir && it.name.equals(part, true) }) {
                    current.add(Node(part, false, size, extOf(part)))
                }
            } else {
                accumulated += "/$part"
                current = directoryIndex[accumulated] ?: run {
                    val created = Node(part, true)
                    current.add(created)
                    directoryIndex[accumulated] = created
                    created
                }
            }
        }
    }

    private object CharsetForZip {
        val cp437 = java.nio.charset.Charset.forName("CP437")
    }

    companion object {
        private const val MAX_JSON_BYTES = 256L * 1024 * 1024
        private const val MAX_CENTRAL_DIRECTORY_BYTES = 256L * 1024 * 1024
    }
}
