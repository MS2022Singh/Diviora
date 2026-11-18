package com.diviora.music.data.repo

/** Minimal parser for .m3u/.m3u8 (extended or plain) and .pls playlist text. Only http(s) entries are kept. */
object PlaylistParser {
    data class Entry(val url: String, val title: String?)

    fun parse(text: String): List<Entry> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return if (lines.firstOrNull()?.equals("[playlist]", ignoreCase = true) == true) parsePls(lines) else parseM3u(lines)
    }

    private fun parseM3u(lines: List<String>): List<Entry> {
        val out = ArrayList<Entry>()
        var pendingTitle: String? = null
        for (l in lines) {
            when {
                l.startsWith("#EXTINF", ignoreCase = true) -> pendingTitle = l.substringAfter(',', "").trim().ifEmpty { null }
                l.startsWith("#") -> Unit
                isHttp(l) -> { out += Entry(l, pendingTitle); pendingTitle = null }
            }
        }
        return out
    }

    private fun parsePls(lines: List<String>): List<Entry> {
        val files = HashMap<Int, String>(); val titles = HashMap<Int, String>()
        val re = Regex("""(?i)^(File|Title)(\d+)=(.*)$""")
        for (l in lines) {
            val m = re.find(l) ?: continue
            val idx = m.groupValues[2].toInt(); val v = m.groupValues[3].trim()
            if (m.groupValues[1].equals("File", true)) files[idx] = v else titles[idx] = v
        }
        return files.toSortedMap().filterValues(::isHttp).map { (i, url) -> Entry(url, titles[i]) }
    }

    private fun isHttp(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true)
}
