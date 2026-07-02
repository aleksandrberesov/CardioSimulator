package com.example.cardiosimulator.domain

/**
 * Pure-Kotlin parser + serializer for the flat pathology dataset format.
 * Grammar is documented in `docs/data-structure.md` §2 (.dat files) and §3
 * (manifest.txt). Both files are UTF-8, LF, `key:value` per line, blank
 * lines separate blocks.
 */
object PathologyParser {

    class FormatException(message: String) : RuntimeException(message)

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
            if (!e.description.isNullOrBlank()) {
                sb.append(";description:").append(e.description)
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    // ─── <pathology>.dat ────────────────────────────────────────────────

    fun parsePathology(text: String): PathologyFile {
        val blocks = splitBlocks(text)
        if (blocks.isEmpty()) throw FormatException("pathology: empty file")

        val header = blocks.first()
        val id = header["pathology"] ?: throw FormatException("pathology: missing 'pathology'")
        val title = header["title"].orEmpty()
        val name = header["name"]
        val group = header["group"]
        val description = header["description"]?.replace("\\n", "\n")
        val globalMarkers = parseMarkers(header["markers"])

        val leadBlocks = blocks.drop(1)
        val leads = linkedMapOf<Lead, LeadStream>()
        for (block in leadBlocks) {
            val leadToken = block["lead"] ?: continue
            val lead = Lead.fromToken(leadToken)
                ?: throw FormatException("pathology[$id]: unknown lead '$leadToken'")
            val count = block["count"]?.trim()?.toIntOrNull()
                ?: throw FormatException("pathology[$id]: lead $lead missing 'count'")
            val pointsField = block["points"]
                ?: throw FormatException("pathology[$id]: lead $lead missing 'points'")
            val samples = parseIntCsv(pointsField)
            if (samples.size != count) {
                throw FormatException(
                    "pathology[$id]: lead $lead 'count' says $count but parsed ${samples.size} samples"
                )
            }

            leads[lead] = LeadStream(lead, samples)
        }
        return PathologyFile(id, title, name, leads, globalMarkers, group, description)
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
        sb.append("leads:").append(file.leads.size).append('\n')
        
        if (file.significantPoints.isNotEmpty()) {
            sb.append("markers:")
            file.significantPoints.forEachIndexed { i, pt ->
                if (i > 0) sb.append(',')
                sb.append(pt.index).append(':').append(pt.type.name)
            }
            sb.append('\n')
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
}
