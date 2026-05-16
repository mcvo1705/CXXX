```kotlin
package com.Sextb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody
import org.jsoup.nodes.Element

class SextbProvider : MainAPI() {

    override var mainUrl              = "https://sextb.date"
    override var name                 = "Sextb"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    private val ajaxUrl by lazy {
        "$mainUrl/ajax/player"
    }

    override val mainPage = mainPageOf(
        "/amateur" to "Amateur",
        "/censored" to "Censored",
        "/uncensored" to "Uncensored",
        "/subtitle" to "English Subtitled"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(
            "$mainUrl${request.data}/pg-$page",
            headers = defaultHeaders
        ).document

        val items = document
            .select(".tray-item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(
                request.name,
                items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private val defaultHeaders = mapOf(
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "User-Agent" to USER_AGENT
    )

    private fun getRequestBody(
        episode: String,
        filmId: String
    ): FormBody {

        return FormBody.Builder()
            .addEncoded("episode", episode)
            .addEncoded("filmId", filmId)
            .build()
    }

    private fun Element.toSearchResult(): SearchResponse {

        val title = select(".tray-item-title")
            .text()
            .trim()

        val href = fixUrl(
            selectFirst("a")
                ?.attr("href")
                .orEmpty()
        )

        val poster = selectFirst(".tray-item-thumbnail")
            ?.attr("data-src")

        return newMovieSearchResponse(
            title,
            href,
            TvType.NSFW
        ) {
            posterUrl = poster
        }
    }

    override suspend fun search(
        query: String,
        page: Int
    ): SearchResponseList? {

        val document = app.get(
            "$mainUrl/search/${query.replace(" ", "-")}/pg-$page",
            headers = defaultHeaders
        ).document

        val results = document
            .select(".tray-item")
            .mapNotNull { it.toSearchResult() }

        return newSearchResponseList(
            results,
            results.isNotEmpty()
        )
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(
            url,
            headers = defaultHeaders
        ).document

        val title = document
            .selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.replace("| PornHoarder.tv", "")
            ?.trim()
            ?: "Unknown"

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")
        )

        val description = document
            .selectFirst("meta[property=og:description]")
            ?.attr("content")
            ?.trim()

        return newMovieLoadResponse(
            title,
            url,
            TvType.NSFW,
            url
        ) {
            posterUrl = poster
            plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(
            data,
            headers = defaultHeaders
        ).document

        val episodeButtons = document.select(
            ".episode-list .btn-player"
        )

        if (episodeButtons.isEmpty()) {
            return false
        }

        val sourceId = episodeButtons
            .firstOrNull()
            ?.attr("data-source")
            .orEmpty()

        episodeButtons.forEach { button ->

            val episodeId = button.attr("data-id")

            if (episodeId.isBlank()) return@forEach

            val serverName = button.text()
                .trim()
                .ifBlank { "Server" }

            val requestBody = getRequestBody(
                episodeId,
                sourceId
            )

            val response = app.post(
                ajaxUrl,
                requestBody = requestBody,
                headers = mapOf(
                    "Referer" to data,
                    "Origin" to mainUrl,
                    "X-Requested-With" to "XMLHttpRequest",
                    "User-Agent" to USER_AGENT
                )
            ).document

            val iframeElements = response.select("iframe")

            iframeElements.forEach { iframe ->

                var iframeUrl = iframe.attr("src")

                iframeUrl = iframeUrl
                    .replace("\\/", "/")
                    .replace("\\\"", "")
                    .trim()

                if (iframeUrl.startsWith("//")) {
                    iframeUrl = "https:$iframeUrl"
                }

                if (iframeUrl.isBlank()) {
                    return@forEach
                }

                loadExtractor(
                    iframeUrl,
                    subtitleCallback
                ) { link ->

                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name - $serverName",
                            url = link.url,
                            referer = mainUrl,
                            quality = link.quality,
                            isM3u8 = link.isM3u8,
                            headers = link.headers,
                            extractorData = link.extractorData
                        )
                    )
                }
            }
        }

        return true
    }
}
```
