package com.example.cardiosimulator.domain

/**
 * In-app clipboard for anchor lists. Single static slot is enough — the
 * editor only ever cuts/pastes one selection at a time, and there is no
 * cross-app clipboard interop.
 */
object AnchorClipboard {
    @Volatile
    private var content: List<AnchorPoint> = emptyList()

    fun set(anchors: List<AnchorPoint>) { content = anchors.toList() }
    fun get(): List<AnchorPoint> = content
    val hasContent: Boolean get() = content.isNotEmpty()
    fun clear() { content = emptyList() }
}

/**
 * Auto-fill helpers. The title is the human-readable name (`pathology + lead`);
 * identy is the stable key used by serialized data files.
 */
object PartNamer {
    fun makeTitle(pathology: String?, lead: Lead?): String {
        val pat = pathology?.trim().orEmpty()
        val ld = lead?.name.orEmpty()
        return if (pat.isEmpty()) ld
        else if (ld.isEmpty()) pat
        else "$pat $ld"
    }

    fun makeIdenty(pathology: String?, lead: Lead?, suffix: String = ""): String {
        val pat = pathology?.trim()?.lowercase()?.replace(' ', '_').orEmpty()
        val ld = lead?.name?.lowercase().orEmpty()
        val base = listOf(pat, ld).filter { it.isNotEmpty() }.joinToString("_")
        return if (suffix.isEmpty()) base else "${base}_$suffix"
    }
}
