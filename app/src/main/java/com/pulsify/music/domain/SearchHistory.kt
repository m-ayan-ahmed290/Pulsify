package com.pulsify.music.domain

fun rememberedSearches(previous: List<String>, query: String): List<String> {
    val term = query.trim().take(120)
    if(term.isBlank()) return previous
    return (listOf(term) + previous.filterNot { it.equals(term, true) }).take(8)
}
