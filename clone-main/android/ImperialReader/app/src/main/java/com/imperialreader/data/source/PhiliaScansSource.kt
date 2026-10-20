package com.imperialreader.data.source

import com.imperialreader.util.parseChapterNumber
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException

class PhiliaScansSource : HttpSource() {
    override val id = "wp_philiascans_com"
    override val name = "PhiliaScans"
    override val lang = "en"
    override val baseUrl = "https://philiascans.org"

    private val wordpress = WordPressSource(name, baseUrl, lang, idOverride = id)

    override fun imageHeaders(): Map<String, String> = wordpress.imageHeaders()

    override suspend fun getPopular(page: Int) = wordpress.getPopular(page)

    override suspend fun getLatest(page: Int) = wordpress.getLatest(page)

    override suspend fun search(query: String, page: Int) = wordpress.search(query, page)

    override suspend fun getMangaDetails(manga: SManga) = wordpress.getMangaDetails(manga)

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val mangaSlug = runCatching { URI(manga.url).path.trimEnd('/').substringAfterLast('/') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("PhiliaScans returned an invalid manga address")
        val json = JSONObject(getString("$baseUrl/api/manga/${enc(mangaSlug)}/chapters"))
        val items = json.optJSONArray("items")
            ?: throw IOException("PhiliaScans returned no chapter list")
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.toChapter(mangaSlug)
        }.also {
            if (it.isEmpty()) throw IOException("PhiliaScans returned no chapters for this manga")
        }
    }

    private fun JSONObject.toChapter(mangaSlug: String): SChapter? {
        val slug = optString("slug").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val number = optString("number").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val title = optString("title").takeIf { it.isNotBlank() && it != "null" && it != number }
        val locked = optInt("coinPrice") != 0 && !optBoolean("purchased")
        val name = buildString {
            if (locked) append("Locked - ")
            append("Chapter ").append(number)
            if (title != null) append(" - ").append(title)
        }
        val date = optString("publishedAt").takeIf { it.isNotBlank() && it != "null" }
        val dateUpload = try {
            date?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        } catch (_: DateTimeParseException) {
            0L
        }
        return SChapter(
            url = "$baseUrl/series/$mangaSlug/$slug",
            name = name,
            dateUpload = dateUpload,
            chapterNumber = number.toFloatOrNull() ?: parseChapterNumber(number)
        )
    }

    override suspend fun getPages(chapter: SChapter) = wordpress.getPages(chapter)
}
