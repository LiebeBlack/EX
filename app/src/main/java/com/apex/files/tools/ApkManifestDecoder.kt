package com.apex.files.tools

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Native, zero-dependency decoder for Android binary XML (`AndroidManifest.xml`)
 * and APK containers. Parses the AXML chunk format directly from bytes:
 *
 *  - 0x0001 string pool (UTF-8 / UTF-16)
 *  - 0x0100/0x0101 namespace open/close
 *  - 0x0102/0x0103 start/end element (typed attribute values)
 *
 * and digs a digest out of the resulting element tree, without touching
 * [android.content.pm.PackageManager]. Supports single `.apk` files and
 * `.xapk` / `.apks` / `.apkm` containers (the inner APK is re-parsed through
 * its own zip stream), and extracts a best-effort launcher icon (PNG/WebP).
 */
object ApkManifestDecoder {

    const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    const val RES_AUTO_NS_URI = "http://schemas.android.com/apk/res-auto"
    private const val NO_INDEX = 0xFFFFFFFFL

    val CONTAINER_EXTS: Set<String> = setOf("xapk", "apks", "apkm")

    private const val MANIFEST_CAP = 8 * 1024 * 1024
    private const val ICON_CAP = 4 * 1024 * 1024

    // ------------------------------------------------------------------ API

    /** A single decoded attribute (name already carries no prefix). */
    data class Attr(val ns: String?, val name: String, val value: String)

    /** One decoded XML element of the tree. */
    data class MElement(
        val name: String,
        val nsPrefix: String? = null,
        val attrs: List<Attr> = emptyList(),
        val children: MutableList<MElement> = ArrayList(),
    )

    data class ManifestSummary(
        val packageName: String? = null,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val minSdk: Long? = null,
        val targetSdk: Long? = null,
        val compileSdk: Long? = null,
        val debuggable: Boolean? = null,
        val label: String? = null,
        val iconRef: String? = null,
        val permissions: List<String> = emptyList(),
        val features: List<String> = emptyList(),
        val components: Map<String, Int> = emptyMap(),
        val componentSamples: Map<String, List<String>> = emptyMap(),
        val hasApplication: Boolean = false,
    )

    data class DeepInfo(
        val fileName: String = "",
        val kind: String = "", // apk | xapk | apks | apkm
        val summary: ManifestSummary? = null,
        val splits: List<String> = emptyList(),
        val decodedXml: String? = null,
        val iconBytes: ByteArray? = null,
        val error: String? = null,
    )

    fun isContainer(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in CONTAINER_EXTS

    fun inspect(file: File): DeepInfo {
        val kind = file.name.substringAfterLast('.', "").lowercase()
        val okKind = kind == "apk" || kind in CONTAINER_EXTS
        if (!file.isFile || !okKind) {
            return DeepInfo(fileName = file.name, kind = kind, error = "No es un paquete Android (.apk/.xapk/.apks)")
        }
        try {
            ZipFile(file).use { zf ->
                return when {
                    kind == "apk" -> inspectSingle(zf, file.name)
                    else -> inspectContainer(zf, file.name)
                }
            }
        } catch (e: Exception) {
            return DeepInfo(
                fileName = file.name,
                kind = kind,
                error = "No se pudo analizar: ${e.message ?: "ZIP inválido"}",
            )
        }
    }

    // ------------------------------------------------------------ inspection

    private fun inspectSingle(zf: ZipFile, fileName: String): DeepInfo {
        var manifestBytes: ByteArray? = null
        var iconBytes: ByteArray? = null
        var bestIconScore = -1

        val entries = zf.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (manifestBytes == null && entry.name == "AndroidManifest.xml") {
                manifestBytes = readEntry(zf, entry, MANIFEST_CAP)
            }
            if (iconBytes == null) {
                val score = iconScore(entry.name)
                if (score > bestIconScore) {
                    val bytes = readEntry(zf, entry, ICON_CAP)
                    if (bytes != null) {
                        bestIconScore = score
                        iconBytes = bytes
                    }
                }
            }
            if (manifestBytes != null && iconBytes != null) break
        }
        return buildResult(fileName, "apk", manifestBytes, iconBytes)
    }

    private fun inspectContainer(zf: ZipFile, fileName: String): DeepInfo {
        val innerApks = ArrayList<String>()
        var outerIconBytes: ByteArray? = null
        var outerIconScore = -1
        var baseEntry: ZipEntry? = null
        var baseScore = -1

        val entries = zf.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name
            if (!entry.isDirectory) {
                if (name.endsWith(".apk", ignoreCase = true)) {
                    innerApks.add(name)
                    val score = baseEntryScore(name)
                    if (score > baseScore) {
                        baseScore = score
                        baseEntry = entry
                    }
                }
                val score = iconScore(name)
                if (score > outerIconScore) {
                    val bytes = readEntry(zf, entry, ICON_CAP)
                    if (bytes != null) {
                        outerIconScore = score
                        outerIconBytes = bytes
                    }
                }
            }
        }

        // No APK inside: nothing to analyze.
        val base = baseEntry ?: return DeepInfo(
            fileName = fileName,
            kind = fileName.substringAfterLast('.', "").lowercase(),
            splits = innerApks,
            error = "El contenedor no incluye ningún .apk",
        )

        // Re-parse the inner APK through its own zip stream (single pass:
        // capture manifest + best raster icon, then stop).
        val inner = try {
            zf.getInputStream(base).use { readInnerApk(it) }
        } catch (e: Exception) {
            null
        }
        if (inner == null) {
            return DeepInfo(
                fileName = fileName,
                kind = fileName.substringAfterLast('.', "").lowercase(),
                splits = innerApks,
                error = "No se pudo leer el APK interno",
            )
        }

        // Prefer the outer icon when the container ships one (some xapk keep
        // icon.png at root); otherwise fall back to the inner icon.
        val icon = outerIconBytes ?: inner.iconBytes
        return buildResult(
            fileName = fileName,
            kind = fileName.substringAfterLast('.', "").lowercase(),
            manifestBytes = inner.manifestBytes,
            iconBytes = icon,
            splits = innerApks,
        )
    }

    /** Inner APK data holder produced by a single-pass stream scan. */
    private class InnerApk {
        var manifestBytes: ByteArray? = null
        var iconBytes: ByteArray? = null
        var iconScore = -1
    }

    private fun readInnerApk(input: InputStream): InnerApk? {
        val out = InnerApk()
        val zis = ZipInputStream(input.buffered())
        while (true) {
            val entry = zis.nextEntry ?: break
            if (out.manifestBytes == null && entry.name == "AndroidManifest.xml") {
                out.manifestBytes = readLimited(zis, MANIFEST_CAP)
            }
            if (out.iconBytes == null) {
                val score = iconScore(entry.name)
                if (score > out.iconScore) {
                    val bytes = readLimited(zis, ICON_CAP)
                    if (bytes != null) {
                        out.iconScore = score
                        out.iconBytes = bytes
                    }
                }
            }
            zis.closeEntry()
            if (out.manifestBytes != null && out.iconBytes != null) break
        }
        if (out.manifestBytes == null) return null
        return out
    }

    private fun buildResult(
        fileName: String,
        kind: String,
        manifestBytes: ByteArray?,
        iconBytes: ByteArray?,
        splits: List<String> = emptyList(),
    ): DeepInfo {
        if (manifestBytes == null) {
            return DeepInfo(
                fileName = fileName,
                kind = kind,
                splits = splits,
                error = "No se encontró AndroidManifest.xml",
            )
        }
        val roots = parseAxml(manifestBytes)
        if (roots == null) {
            return DeepInfo(
                fileName = fileName,
                kind = kind,
                splits = splits,
                error = "AndroidManifest.xml binario no legible",
            )
        }
        return DeepInfo(
            fileName = fileName,
            kind = kind,
            summary = digest(roots),
            splits = splits,
            decodedXml = renderXml(roots),
            iconBytes = iconBytes,
        )
    }

    // -------------------------------------------------------------- strings

    private fun readEntry(zf: ZipFile, entry: ZipEntry, cap: Int): ByteArray? {
        if (!entry.isDirectory && entry.size in 1..cap.toLong()) {
            return zf.getInputStream(entry).use { readLimited(it, cap) }
        }
        return null
    }

    /** Reads up to [cap] bytes; null when the stream exceeds the cap. */
    private fun readLimited(input: InputStream, cap: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream(minOf(cap, 64 * 1024))
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > cap) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun baseEntryScore(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.endsWith("/base.apk") -> 3
            "/base.apk" in lower -> 3
            lower.contains("base") -> 2
            else -> 1
        }
    }

    /**
     * Raster launcher-icon candidates get a score (higher = better); -1 means
     * “not a launcher icon”. Adaptive foreground/background/monochrome layers
     * are rejected (they are translucent fragments, not the icon itself).
     */
    private fun iconScore(name: String): Int {
        val lower = name.lowercase()
        val isRaster = lower.endsWith(".png") || lower.endsWith(".webp")
        if (!isRaster) return -1
        val resIdx = lower.indexOf("/res/")
        if (resIdx < 0) return -1
        val afterRes = lower.substring(resIdx + 5)
        val dirSegment = afterRes.substringBefore('/')
        if (!dirSegment.startsWith("mipmap") && !dirSegment.startsWith("drawable")) return -1
        val fileName = afterRes.substringAfterLast('/')
        val base = fileName.substringBeforeLast('.')
        val iconish = base == "icon" || base == "app_icon" ||
            (base.startsWith("ic_launcher") &&
                !base.contains("foreground") &&
                !base.contains("background") &&
                !base.contains("monochrome"))
        if (!iconish) return -1
        val densities = listOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val tokens = dirSegment.split('-')
        val densityIdx = tokens.indexOfFirst { it in densities }
        return 1000 + if (densityIdx >= 0) densityIdx else 0
    }

    // ------------------------------------------------------------- AXML core

    private class Reader(private val b: ByteArray) {
        fun u16(i: Int): Int =
            if (i + 2 <= b.size) (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) else 0

        fun u32(i: Int): Long =
            if (i + 4 <= b.size) {
                (b[i].toLong() and 0xFF) or
                    ((b[i + 1].toLong() and 0xFF) shl 8) or
                    ((b[i + 2].toLong() and 0xFF) shl 16) or
                    ((b[i + 3].toLong() and 0xFF) shl 24)
            } else 0L

        fun u8(i: Int): Int = if (i < b.size) b[i].toInt() and 0xFF else 0
    }

    /** Full AXML parse → element forest; null on any structural corruption. */
    private fun parseAxml(bytes: ByteArray): List<MElement>? {
        if (bytes.size < 8) return null
        val r = Reader(bytes)
        if (r.u16(0) != 0x0003) return null // RES_XML_TYPE
        if (r.u32(4) != bytes.size.toLong()) return null

        val roots = ArrayList<MElement>()
        val elementStack = ArrayList<MElement>()
        var pool: List<String> = emptyList()

        fun poolString(idx: Long): String? {
            if (idx == NO_INDEX) return null
            val i = idx.toInt()
            if (i < 0 || i >= pool.size) return null
            return pool[i]
        }

        var pos = 8
        while (pos + 8 <= bytes.size) {
            val type = r.u16(pos)
            val headerSize = r.u16(pos + 2)
            val chunkSizeL = r.u32(pos + 4)
            if (chunkSizeL < 8L || headerSize < 8 || pos + chunkSizeL > bytes.size) return null
            val chunkSize = chunkSizeL.toInt()

            when (type) {
                0x0001 -> pool = readStringPool(r, bytes, pos, chunkSize) ?: return null

                0x0102 -> {
                    // Start element. headerSize is 0x10 (node header); the
                    // ResXMLTree_attrExt fields start right after it.
                    val body = pos + headerSize
                    if (body + 20 > pos + chunkSize) return null
                    val nsIdx = r.u32(body)
                    val nameIdx = r.u32(body + 4)
                    val attrStart = r.u16(body + 8)
                    val attrSize = r.u16(body + 10)
                    val attrCount = r.u16(body + 12)
                    val name = poolString(nameIdx) ?: return null
                    val nsLabel = if (nsIdx == NO_INDEX) null else shortNs(poolString(nsIdx))

                    val attrs = ArrayList<Attr>(attrCount)
                    val attrBase = body + attrStart
                    var a = 0
                    while (a < attrCount) {
                        if (attrSize < 20 || a.toLong() * attrSize + attrBase + 20 > pos.toLong() + chunkSize) return null
                        val at = attrBase + a * attrSize
                        val aNs = r.u32(at)
                        val aNameIdx = r.u32(at + 4)
                        val rawIdx = r.u32(at + 8)
                        val valueType = r.u8(at + 15)
                        val valueData = r.u32(at + 16)
                        val aName = poolString(aNameIdx) ?: return null
                        val aNsLabel = if (aNs == NO_INDEX) null else shortNs(poolString(aNs))
                        val display = renderValue(r, pool, rawIdx, valueType, valueData)
                        attrs.add(Attr(aNsLabel, aName, display))
                        a++
                    }

                    val element = MElement(name, nsLabel, attrs)
                    if (elementStack.isNotEmpty()) elementStack.last().children.add(element) else roots.add(element)
                    elementStack.add(element)
                }

                0x0103 -> {
                    if (elementStack.isEmpty()) return null
                    elementStack.removeAt(elementStack.lastIndex)
                }
                // 0x0100/0x0101 namespace markers, 0x0104 cdata, 0x0180
                // resource map: no tree impact.
            }
            pos += chunkSize
        }
        if (elementStack.isNotEmpty()) return null
        return roots
    }

    private fun shortNs(uri: String?): String? = when (uri) {
        null -> null
        ANDROID_NS_URI -> "android"
        RES_AUTO_NS_URI -> "app"
        "" -> null
        else -> uri.substringAfterLast('/').ifBlank { uri }
    }

    /** Decodes a [ResStringPool]. Returns null when structurally invalid. */
    private fun readStringPool(r: Reader, b: ByteArray, chunkPos: Int, chunkSize: Int): List<String>? {
        val headerSize = r.u16(chunkPos + 2)
        if (headerSize < 28) return null
        val stringCount = r.u32(chunkPos + 8)
        val flags = r.u32(chunkPos + 16)
        val stringsStart = r.u32(chunkPos + 20)
        if (stringCount == 0L) return emptyList()
        if (stringCount > 1_000_000) return null

        val chunkEnd = chunkPos.toLong() + chunkSize
        val dataBase = chunkPos + stringsStart
        if (dataBase < chunkPos + headerSize.toLong() || dataBase > chunkEnd) return null
        val offsets = chunkPos + headerSize

        val utf8 = (flags and 0x100L) != 0L
        val out = ArrayList<String>(stringCount.toInt())
        for (i in 0 until stringCount.toInt()) {
            val offset = r.u32(offsets + i * 4)
            val valueStart = dataBase + offset
            if (valueStart >= chunkEnd) return null
            var cursor = valueStart

            if (utf8) {
                var first = r.u8(cursor.toInt())
                cursor++
                if ((first and 0x80) != 0) {
                    if (cursor + 1 > chunkEnd) return null
                    first = ((first and 0x7F) shl 8) or r.u8(cursor.toInt())
                    cursor++
                }
                // character length already consumed; next is byte length
                var byteLen = r.u8(cursor.toInt())
                cursor++
                if ((byteLen and 0x80) != 0) {
                    if (cursor + 1 > chunkEnd) return null
                    byteLen = ((byteLen and 0x7F) shl 8) or r.u8(cursor.toInt())
                    cursor++
                }
                if (byteLen < 0 || cursor + byteLen > chunkEnd) return null
                out.add(String(b, cursor.toInt(), byteLen, Charsets.UTF_8))
            } else {
                var charCount = r.u16(cursor.toInt())
                cursor += 2
                if ((charCount and 0x8000) != 0) {
                    if (cursor + 2 > chunkEnd) return null
                    charCount = ((charCount and 0x7FFF) shl 16) or r.u16(cursor.toInt())
                    cursor += 2
                }
                if (cursor + charCount.toLong() * 2 > chunkEnd) return null
                val sb = StringBuilder(charCount)
                for (c in 0 until charCount) {
                    sb.append(r.u16(cursor.toInt() + c * 2).toChar())
                }
                out.add(sb.toString())
            }
        }
        return out
    }

    private fun renderValue(
        r: Reader,
        pool: List<String>,
        rawIdx: Long,
        valueType: Int,
        data: Long,
    ): String {
        if (rawIdx != NO_INDEX) {
            val i = rawIdx.toInt()
            if (i in pool.indices) return pool[i]
        }
        return when (valueType) {
            0x03 -> { // TYPE_STRING
                val i = data.toInt()
                if (i in pool.indices) pool[i] else ""
            }
            0x10 -> data.toInt().toString() // TYPE_INT_DEC
            0x11, 0x1c, 0x1d, 0x1e, 0x1f -> "0x" + hex8(data) // INT_HEX / colors
            0x12 -> if (data != 0L) "true" else "false" // TYPE_INT_BOOLEAN
            0x01, 0x02 -> "@0x" + hex8(data) // TYPE_REFERENCE / ATTRIBUTE
            0x04 -> { // TYPE_FLOAT
                val f = Float.fromBits(data.toInt())
                if (f.isNaN()) "0x" + hex8(data) else f.toString()
            }
            else -> "0x" + hex8(data)
        }
    }

    private fun hex8(v: Long): String =
        (v and 0xFFFFFFFFL).toString(16).padStart(8, '0')

    // ------------------------------------------------------------- digest

    private fun attrOf(el: MElement, name: String, ns: String?): String? =
        el.attrs.firstOrNull { it.name == name && it.ns == ns }?.value

    private fun intAttr(el: MElement, name: String, ns: String?): Long? {
        val raw = attrOf(el, name, ns)?.trim() ?: return null
        return if (raw.startsWith("0x")) {
            raw.removePrefix("0x").toLongOrNull(16)
        } else {
            raw.toLongOrNull()
        }
    }

    private fun digest(roots: List<MElement>): ManifestSummary {
        val manifest = roots.firstOrNull { it.name == "manifest" }
        if (manifest == null) return ManifestSummary(packageName = roots.firstOrNull()?.name)

        val packageName = attrOf(manifest, "package", null)
        val application = manifest.children.firstOrNull { it.name == "application" }
        val usesSdk = manifest.children.firstOrNull { it.name == "uses-sdk" }

        val permissions = ArrayList<String>()
        val features = ArrayList<String>()
        for (child in manifest.children) {
            when (child.name) {
                "uses-permission" -> attrOf(child, "name", "android")?.let { permissions.add(it) }
                "uses-feature" -> {
                    val v = attrOf(child, "name", "android") ?: attrOf(child, "glEsVersion", "android")
                    if (v != null) features.add(v)
                }
            }
        }

        val componentSamples = HashMap<String, ArrayList<String>>()
        val componentCounts = HashMap<String, Int>()
        if (application != null) {
            fun collect(container: MElement) {
                for (child in container.children) {
                    val tag = child.name
                    val isComponent = tag in COMPONENT_TAGS
                    if (isComponent) {
                        val counts = componentCounts.getOrPut(tag) { 0 }
                        componentCounts[tag] = counts + 1
                        var name = attrOf(child, "name", "android")
                        if (name != null && name.startsWith(".") && packageName != null) {
                            name = packageName + name
                        }
                        if (name != null) {
                            val samples = componentSamples.getOrPut(tag) { ArrayList() }
                            if (samples.size < 40) samples.add(name)
                        }
                    }
                    // components can nest (e.g. activity-alias inside
                    // application, receivers inside merged manifests)
                    collect(child)
                }
            }
            collect(application)
        }

        val debugRaw = if (application != null) attrOf(application, "debuggable", "android") else null

        return ManifestSummary(
            packageName = packageName,
            versionName = attrOf(manifest, "versionName", "android"),
            versionCode = intAttr(manifest, "versionCode", "android"),
            minSdk = if (usesSdk != null) intAttr(usesSdk, "minSdkVersion", "android") else null,
            targetSdk = if (usesSdk != null) intAttr(usesSdk, "targetSdkVersion", "android") else null,
            compileSdk = if (usesSdk != null) intAttr(usesSdk, "compileSdkVersion", "android") else null,
            debuggable = debugRaw?.toBooleanStrictOrNull(),
            label = if (application != null) attrOf(application, "label", "android") else null,
            iconRef = if (application != null) attrOf(application, "icon", "android") else null,
            permissions = permissions.distinct().take(500),
            features = features.distinct().take(200),
            components = componentCounts,
            componentSamples = componentSamples.mapValues { it.value.toList() },
            hasApplication = application != null,
        )
    }

    private val COMPONENT_TAGS = setOf("activity", "activity-alias", "service", "receiver", "provider")

    // ------------------------------------------------------------- rendering

    /** Human-readable indented XML of the decoded tree (line-capped). */
    private fun renderXml(roots: List<MElement>): String {
        val sb = StringBuilder(1024)
        var lines = 0
        val maxLines = 2_000

        fun tagName(el: MElement): String =
            if (el.nsPrefix != null) "${el.nsPrefix}:${el.name}" else el.name

        fun emit(el: MElement, depth: Int) {
            if (lines >= maxLines) return
            repeat(depth) { sb.append("  ") }
            sb.append('<').append(tagName(el))
            for (attr in el.attrs) {
                val prefix = if (attr.ns != null) "${attr.ns}:" else ""
                sb.append(' ').append(prefix).append(attr.name).append("=\"")
                sb.append(
                    attr.value
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                )
                sb.append('"')
            }
            if (el.children.isEmpty()) {
                sb.append("/>\n")
                lines++
            } else {
                sb.append(">\n")
                lines++
                for (child in el.children) emit(child, depth + 1)
                if (lines < maxLines) {
                    repeat(depth) { sb.append("  ") }
                    sb.append("</").append(tagName(el)).append(">\n")
                    lines++
                }
            }
        }

        for (root in roots) emit(root, 0)
        if (lines >= maxLines) {
            sb.append("\n… (XML truncado)\n")
        }
        return sb.toString()
    }
}
