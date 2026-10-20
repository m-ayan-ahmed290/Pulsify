package com.pulsify.music.domain

const val SUPPORT_URL = "https://www.buymeacoffee.com/bunnyfrost"
const val SUPPORT_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

fun supportPromptDue(now: Long, lastShown: Long?): Boolean =
    lastShown == null || (now >= lastShown && now - lastShown >= SUPPORT_INTERVAL_MS)
