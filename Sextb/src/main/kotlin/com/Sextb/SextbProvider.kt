package com.Sextb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

class SextbProvider : MainAPI() {

    override var mainUrl = "https://sextb.net"
    override var name = "Sextb"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val ajaxUrl = "$mainUrl/ajax/player"

    private val domains = listOf(
        "https://sextb.net",
        "https://sextb.date"
    )

    override val mainPage = mainPageOf(
        "/amateur" to "Amateur",
        "/censored" to "Censored",
        "/uncensored" to "Uncensored",
        "/subtitle" to "English Subtitled"
    )

    // ===================== MAIN PAGE =====================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}/pg-$page").document

        val results = document.select(".tray-item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, results, isHorizontalImages = false),
            hasNext = results.isNotEmpty()
        )
    }

    // ===================== SEARCH =====================
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = URLEncoder.encode(query, "UTF-8")

        val document = app.get("$mainUrl/search/$encoded/pg-$page").document

        val results = document.select(".tray-item")
            .mapNotNull { it.toSearchResult() }

        return newSearchResponseList(results, results.isNotEmpty())
    }

    // ===================== LOAD =====================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.replace("| PornHoarder.tv", "")
            ?.trim()
            ?: "Unknown"

        val poster = document.selectFirst("meta[property=og:image]")
            ?.attr("content")

        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster ?: ""
            this.plot = description
        }
    }

    // ===================== LINKS =====================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val sourceId = document
            .selectFirst(".episode-list .btn-player")
            ?.attr("data-source")
            ?: return false

        // 👉 CHỈ lấy episode đầu để tránh spam request
        val episode = document.select(".episode-list .btn-player").firstOrNull()
            ?: return false

        val episodeId = episode.attr("data-id")

        val body = FormBody.Builder()
            .add("episode", episodeId)
            .add("filmId", sourceId)
            .build()

        val responseText = app.post(
            ajaxUrl,
            requestBody = body,
            headers = mapOf(
                "Referer" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).text

        val responseDoc = Jsoup.parse(responseText)

        val iframes = responseDoc.select("iframe")

        iframes.forEachIndexed { index, iframe ->

            val iframeSrc = iframe.attr("src")
                .substringBefore("?")
                .trim()

            if (iframeSrc.isBlank()) return@forEachIndexed

            loadExtractor(
                iframeSrc,
                mainUrl,
                subtitleCallback
            ) { link ->

                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name Server ${index + 1}",
                        url = link.url,
                        referer = mainUrl,
                        quality = link.quality,
                        type = link.type,
                        headers = link.headers
                    )
                )
            }
        }

        return true
    }

    // ===================== HELPERS =====================
    private fun Element.toSearchResult(): SearchResponse {
        val title = select(".tray-item-title").text().trim()
        val href = mainUrl + select("a:nth-of-type(1)").attr("href")
        val poster = selectFirst(".tray-item-thumbnail")?.attr("data-src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }
}
