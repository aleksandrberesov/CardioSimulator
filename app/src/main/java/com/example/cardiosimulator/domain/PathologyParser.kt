package com.example.cardiosimulator.domain

/**
 * Pure-Kotlin parser + serializer for the flat pathology dataset format.
 * Grammar is documented in `docs/data-structure.md` §2 (.dat files) and §3
 * (manifest.txt). Both files are UTF-8, LF, `key:value` per line, blank
 * lines separate blocks.
 */
object PathologyParser {

    class FormatException(message: String) : RuntimeException(message)

    private val CSD1_MAGIC = byteArrayOf(0x43, 0x53, 0x44, 0x31) // "CSD1"

    // ─── manifest.txt ───────────────────────────────────────────────────

    fun parseManifest(text: String): PathologyManifest {
        val (header, body) = splitHeader(text)
        val version = header["version"]
            ?: throw FormatException("manifest: missing 'version'")
        if (version != PathologyManifest.SUPPORTED_VERSION) {
            throw FormatException(
                "manifest: unsupported version '$version' (this build needs " +
                    "'${PathologyManifest.SUPPORTED_VERSION}')"
            )
        }
        val baseline = header["baseline"]?.trim()?.toIntOrNull()
            ?: throw FormatException("manifest: missing or non-integer 'baseline'")
        val leadOrder = header["lead_order"]
            ?.split(',')
            ?.mapNotNull { Lead.fromToken(it) }
            ?: throw FormatException("manifest: missing 'lead_order'")

        val entries = body.mapNotNull { line ->
            val fields = parseSemicolonFields(line)
            val id = fields["pathology"] ?: return@mapNotNull null
            PathologyEntry(
                id = id,
                titleEn = fields["title"].orEmpty(),
                nameRu = fields["name"],
                leadsCount = fields["leads"]?.toIntOrNull() ?: 0,
                fileName = "$id.dat",
                group = fields["group"],
                description = fields["description"],
                clinicalCase = fields["clinical_case"],
                number = fields["number"]?.toIntOrNull(),
                acronym = fields["acronym"],
            )
        }

        return PathologyManifest(
            version = version,
            baseline = baseline,
            leadOrder = leadOrder,
            entries = entries,
        )
    }

    fun serializeManifest(manifest: PathologyManifest): String {
        val sb = StringBuilder()
        sb.append("version:").append(manifest.version).append('\n')
        sb.append("baseline:").append(manifest.baseline).append('\n')
        sb.append("lead_order:")
            .append(manifest.leadOrder.joinToString(",") { it.name })
            .append('\n')
        sb.append("pathologies:").append(manifest.entries.size).append('\n')
        sb.append('\n')
        for (e in manifest.entries) {
            sb.append("pathology:").append(e.id)
                .append(";leads:").append(e.leadsCount)
                .append(";title:").append(e.titleEn)
            if (!e.nameRu.isNullOrBlank()) {
                sb.append(";name:").append(e.nameRu)
            }
            if (!e.group.isNullOrBlank()) {
                sb.append(";group:").append(e.group)
            }

            if (!e.clinicalCase.isNullOrBlank()) {
                sb.append(";clinical_case:").append(e.clinicalCase)
            }
            if (e.number != null) {
                sb.append(";number:").append(e.number)
            }
            if (!e.acronym.isNullOrBlank()) {
                sb.append(";acronym:").append(e.acronym)
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    // ─── <pathology>.dat ────────────────────────────────────────────────

    fun parsePathology(bytes: ByteArray): PathologyFile {
        if (bytes.isEmpty()) throw FormatException("pathology: empty file")
        return if (hasMagic(bytes)) {
            parsePathologyBinary(bytes)
        } else {
            parsePathology(decodeUtf8(bytes))
        }
    }

    fun parsePathology(text: String): PathologyFile {
        val blocks = splitBlocks(text)
        if (blocks.isEmpty()) throw FormatException("pathology: empty file")

        val header = blocks.first()
        val leadBlocks = blocks.drop(1)
        val leads = linkedMapOf<Lead, LeadStream>()
        for (block in leadBlocks) {
            val leadToken = block["lead"] ?: continue
            val lead = Lead.fromToken(leadToken)
                ?: throw FormatException("pathology: unknown lead '$leadToken'")
            val count = block["count"]?.trim()?.toIntOrNull()
                ?: throw FormatException("pathology: lead $lead missing 'count'")
            val pointsField = block["points"]
                ?: throw FormatException("pathology: lead $lead missing 'points'")
            val samples = parseIntCsv(pointsField)
            if (samples.size != count) {
                throw FormatException(
                    "pathology: lead $lead 'count' says $count but parsed ${samples.size} samples"
                )
            }

            leads[lead] = LeadStream(lead, samples)
        }
        return buildFromHeader(header, leads)
    }

    private fun hasMagic(b: ByteArray) = b.size >= 4 &&
            b[0] == CSD1_MAGIC[0] && b[1] == CSD1_MAGIC[1] && b[2] == CSD1_MAGIC[2] && b[3] == CSD1_MAGIC[3]

    private fun decodeUtf8(b: ByteArray): String =
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            String(b, 3, b.size - 3, Charsets.UTF_8) else String(b, Charsets.UTF_8)

    private fun parsePathologyBinary(bytes: ByteArray): PathologyFile {
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.position(CSD1_MAGIC.size)

        val headerText = readString(buf) ?: throw FormatException("pathology: binary missing header")
        val header = splitBlocks(headerText).firstOrNull() ?: emptyMap()

        val leadCount = buf.int
        val leads = linkedMapOf<Lead, LeadStream>()
        repeat(leadCount) {
            val idx = buf.get().toInt() and 0xFF
            if (idx >= Lead.entries.size) throw FormatException("pathology: lead index $idx out of range")
            val lead = Lead.entries[idx]
            readString(buf) // elements text — Android has none; discard
            val n = buf.int
            if (n < 0) throw FormatException("pathology: negative sample count")
            val samples = IntArray(n)
            var prev = 0
            for (i in 0 until n) {
                val v = (prev + buf.short).toShort().toInt()
                samples[i] = v
                prev = v
            }
            leads[lead] = LeadStream(lead, samples)
        }
        return buildFromHeader(header, leads)
    }

    private fun readString(buf: java.nio.ByteBuffer): String? {
        val len = buf.int
        if (len < 0) return null
        val b = ByteArray(len)
        buf.get(b)
        return String(b, Charsets.UTF_8)
    }

    private fun buildFromHeader(header: Map<String, String>, leads: Map<Lead, LeadStream>): PathologyFile {
        val id = header["pathology"] ?: throw FormatException("pathology: missing 'pathology'")
        val title = header["title"].orEmpty()
        val name = header["name"]
        val group = header["group"]
        val description = header["description"]?.replace("\\n", "\n")
        val clinicalCase = header["clinical_case"]
        val number = header["number"]?.trim()?.toIntOrNull()
        val acronym = header["acronym"]
        val globalMarkers = parseMarkers(header["markers"])
        val tips = TipOverlaySerializer.parse(header["tips"])
        val tipComments = parseTipComments(header["tip_notes"])

        return PathologyFile(
            id, title, name, leads, globalMarkers, tips, tipComments,
            group, description, clinicalCase, number, acronym
        )
    }

    fun serializePathology(file: PathologyFile, leadOrder: List<Lead>): String {
        val sb = StringBuilder()
        sb.append("pathology:").append(file.id).append('\n')
        sb.append("title:").append(file.titleEn).append('\n')
        sb.append("name:").append(file.nameRu.orEmpty()).append('\n')
        if (!file.group.isNullOrBlank()) {
            sb.append("group:").append(file.group).append('\n')
        }
        if (!file.description.isNullOrBlank()) {
            val escaped = file.description.replace("\r\n", "\n").replace("\n", "\\n")
            sb.append("description:").append(escaped).append('\n')
        }
        if (!file.clinicalCase.isNullOrBlank()) {
            sb.append("clinical_case:").append(file.clinicalCase).append('\n')
        }
        if (file.number != null) {
            sb.append("number:").append(file.number).append('\n')
        }
        if (!file.acronym.isNullOrBlank()) {
            sb.append("acronym:").append(file.acronym).append('\n')
        }
        sb.append("leads:").append(file.leads.size).append('\n')
        
        if (file.significantPoints.isNotEmpty()) {
            sb.append("markers:")
            file.significantPoints.forEachIndexed { i, pt ->
                if (i > 0) sb.append(',')
                sb.append(pt.index).append(':').append(pt.type.name)
            }
            sb.append('\n')
        }

        if (file.tips.isNotEmpty()) {
            sb.append("tips:").append(TipOverlaySerializer.serialize(file.tips)).append('\n')
        }
        if (file.tipComments.isNotEmpty()) {
            sb.append("tip_notes:").append(serializeTipComments(file.tipComments)).append('\n')
        }

        for (lead in leadOrder) {
            val stream = file.leads[lead] ?: continue
            sb.append('\n')
            sb.append("lead:").append(lead.name).append('\n')
            sb.append("count:").append(stream.samples.size).append('\n')
            
            sb.append("points:")
            stream.samples.forEachIndexed { i, v ->
                if (i > 0) sb.append(',')
                sb.append(v)
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Serializes [file] into CSD1 binary format.
     */
    fun serializePathologyBinary(file: PathologyFile, leadOrder: List<Lead>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(bos)
        
        // Use Little Endian for consistency with Windows CSD1
        fun writeIntLE(v: Int) {
            dos.write(v and 0xFF)
            dos.write((v shr 8) and 0xFF)
            dos.write((v shr 16) and 0xFF)
            dos.write((v shr 24) and 0xFF)
        }
        fun writeShortLE(v: Int) {
            dos.write(v and 0xFF)
            dos.write((v shr 8) and 0xFF)
        }
        fun writeString(s: String?) {
            if (s == null) {
                writeIntLE(-1)
            } else {
                val b = s.toByteArray(Charsets.UTF_8)
                writeIntLE(b.size)
                dos.write(b)
            }
        }

        dos.write(CSD1_MAGIC)
        
        // Header block as text
        val headerSb = StringBuilder()
        headerSb.append("pathology:").append(file.id).append('\n')
        headerSb.append("title:").append(file.titleEn).append('\n')
        headerSb.append("name:").append(file.nameRu.orEmpty()).append('\n')
        if (!file.group.isNullOrBlank()) headerSb.append("group:").append(file.group).append('\n')
        if (!file.description.isNullOrBlank()) {
            val escaped = file.description.replace("\r\n", "\n").replace("\n", "\\n")
            headerSb.append("description:").append(escaped).append('\n')
        }
        if (!file.clinicalCase.isNullOrBlank()) headerSb.append("clinical_case:").append(file.clinicalCase).append('\n')
        if (file.number != null) headerSb.append("number:").append(file.number).append('\n')
        if (!file.acronym.isNullOrBlank()) headerSb.append("acronym:").append(file.acronym).append('\n')
        
        if (file.significantPoints.isNotEmpty()) {
            headerSb.append("markers:")
            file.significantPoints.forEachIndexed { i, pt ->
                if (i > 0) headerSb.append(',')
                headerSb.append(pt.index).append(':').append(pt.type.name)
            }
            headerSb.append('\n')
        }
        if (file.tips.isNotEmpty()) {
            headerSb.append("tips:").append(TipOverlaySerializer.serialize(file.tips)).append('\n')
        }
        if (file.tipComments.isNotEmpty()) {
            headerSb.append("tip_notes:").append(serializeTipComments(file.tipComments)).append('\n')
        }
        
        writeString(headerSb.toString())

        val leadsToWrite = leadOrder.filter { file.leads.containsKey(it) }
        writeIntLE(leadsToWrite.size)
        
        for (lead in leadsToWrite) {
            val stream = file.leads[lead]!!
            dos.writeByte(Lead.entries.indexOf(lead))
            writeString(null) // elements text placeholder
            
            writeIntLE(stream.samples.size)
            var prev = 0
            for (v in stream.samples) {
                val delta = (v - prev).toShort()
                writeShortLE(delta.toInt())
                prev = v
            }
        }
        
        dos.flush()
        return bos.toByteArray()
    }

    // ─── helpers ────────────────────────────────────────────────────────
    // Shared grammar primitives (splitHeader / splitKeyValue /
    // parseSemicolonFields) live in domain/ParserHelpers.kt.

    /**
     * Splits a `.dat` text into its header block + per-lead blocks. Each
     * block becomes a `key→value` map. Blank lines separate blocks.
     */
    private fun splitBlocks(text: String): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        var current = linkedMapOf<String, String>()
        for (raw in text.split('\n')) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    out += current
                    current = linkedMapOf()
                }
                continue
            }
            val (k, v) = splitKeyValue(line) ?: continue
            current[k] = v
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    private fun parseIntCsv(field: String): IntArray {
        if (field.isBlank()) return IntArray(0)
        val tokens = field.split(',')
        val out = IntArray(tokens.size)
        var n = 0
        for (t in tokens) {
            val parsed = t.trim().toIntOrNull() ?: continue
            out[n++] = parsed
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    private fun parseMarkers(field: String?): List<SignificantPoint> {
        if (field == null || field.isBlank()) return emptyList()
        val out = mutableListOf<SignificantPoint>()
        for (token in field.split(',')) {
            val parts = token.split(':')
            if (parts.size != 2) continue
            val index = parts[0].trim().toIntOrNull() ?: continue
            val typeName = parts[1].trim()
            val type = runCatching { EcgPointType.valueOf(typeName) }.getOrNull() ?: continue
            out.add(SignificantPoint(index, type))
        }
        return out
    }

    private fun escapeTipText(text: String?): String {
        if (text == null) return ""
        return text.replace("%", "%25")
            .replace("|", "%7C")
            .replace("~", "%7E")
            .replace("\r", "%0D")
            .replace("\n", "%0A")
    }

    private fun unescapeTipText(text: String): String? {
        if (text.isEmpty()) return null
        return text.replace("%0A", "\n")
            .replace("%0D", "\r")
            .replace("%7E", "~")
            .replace("%7C", "|")
            .replace("%25", "%")
    }

    private fun parseTipComments(field: String?): List<String> {
        if (field.isNullOrBlank()) return emptyList()
        return field.split('~').mapNotNull { unescapeTipText(it) }
    }

    private fun serializeTipComments(comments: List<String>): String {
        return comments.joinToString("~") { escapeTipText(it) }
    }
}
