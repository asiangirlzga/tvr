package com.example.privatevoice.command

import java.util.Locale

data class AppEntry(val label: String, val packageName: String)

/**
 * Finds the installed app that best matches what was said. Speech engines often mis-hear app
 * names ("what's up" for "WhatsApp"), so matching is forgiving: spaces are ignored and small
 * spelling differences are allowed.
 */
object AppMatcher {
    private fun key(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun best(query: String, apps: List<AppEntry>): AppEntry? {
        val q = key(query)
        if (q.length < 2) return null
        var best: AppEntry? = null
        var bestScore = 0.0
        for (app in apps) {
            val l = key(app.label)
            if (l.isEmpty()) continue
            val score = when {
                l == q -> 1.0
                q.length >= 3 && (l.contains(q) || (l.length >= 4 && q.contains(l))) -> 0.9
                else -> 1.0 - levenshtein(q, l).toDouble() / maxOf(q.length, l.length)
            }
            if (score > bestScore || (score == bestScore && best != null && l.length < key(best.label).length)) {
                best = app
                bestScore = score
            }
        }
        return if (bestScore >= 0.7) best else null
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
