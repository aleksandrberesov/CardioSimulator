package com.example.cardiosimulator.cli

/**
 * Tiny GNU-ish long-flag parser. Supports `--flag value`, `--flag=value`,
 * and bare flags. No short flags, no clustering — keep it boring.
 */
class Args(args: Array<String>) {

    private val positional = mutableListOf<String>()
    private val flags = LinkedHashMap<String, MutableList<String>>()

    init {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--")) {
                val body = a.substring(2)
                val eq = body.indexOf('=')
                if (eq >= 0) {
                    val k = body.substring(0, eq)
                    val v = body.substring(eq + 1)
                    flags.getOrPut(k) { mutableListOf() }.add(v)
                } else {
                    val next = args.getOrNull(i + 1)
                    if (next != null && !next.startsWith("--")) {
                        flags.getOrPut(body) { mutableListOf() }.add(next)
                        i++
                    } else {
                        flags.getOrPut(body) { mutableListOf() }.add("")
                    }
                }
            } else {
                positional += a
            }
            i++
        }
    }

    fun positional(index: Int): String? = positional.getOrNull(index)

    fun opt(name: String): String? = flags[name]?.firstOrNull()

    fun req(name: String): String = flags[name]?.firstOrNull()
        ?: error("missing required --$name")

    fun all(name: String): List<String> = flags[name].orEmpty()

    fun flag(name: String): Boolean = flags.containsKey(name)

    fun intOpt(name: String): Int? = opt(name)?.toIntOrNull()
        ?: opt(name)?.let { error("--$name must be an integer, got '$it'") }
}
