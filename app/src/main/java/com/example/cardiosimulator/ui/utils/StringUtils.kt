package com.example.cardiosimulator.ui.utils

/**
 * Adds 5 spaces before and 5 spaces after the given string.
 */
fun String.padWithFiveSpaces(): String {
    val spaces = "     "
    return "$spaces$this$spaces"
}
