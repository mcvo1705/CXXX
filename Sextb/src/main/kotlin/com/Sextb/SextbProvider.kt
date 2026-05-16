package com.Sextb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody
import org.jsoup.nodes.Element

class SextbProvider : MainAPI() {

    override var mainUrl = "https://sextb.date"
    override var name = "Sextb"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    private val ajaxUrl = "$mainUrl/ajax/player"

    override val mainPage = mainPageOf(
        "/amateur" to "Amateur",
        "/censored" to "Censored",
        "/uncensored" to "Uncensored",
        "/subtitle" to "Subtitles"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val doc = app.get("$mainUrl${request.data}/pg-$page").document

        val home = doc.select(".tray-item").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            HomePageList(
                request.name,
                home
            ),
            hasNext = true
        )
    }

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

        val title = select(".tray-item-title").text()

        val href = mainUrl + select("a").attr("href")

        val poster =
            selectFirst(".tray-item-thumbnail")
                ?.attr("data-src")

        return newMovieSearchResponse(
            title,
            href,
            TvType.NSFW
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(
        query: String,
        page: Int
    ): SearchResponseList {

        val doc = app.get(
            "$mainUrl/search/${query.replace(" ", "-")}/pg-$page"
        ).document

        val results = doc.select(".tray-item").mapNotNull {
            it.toSearchResult()
        }

        return newSearchResponseList(
            results,
            hasNext = results.isNotEmpty()
        )
    }

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title =
            doc.selectFirst("meta[property=og:title]")
                ?.attr("content")
                ?.trim()
                ?: "No Title"

        val poster =
            doc.selectFirst("meta[property=og:image]")
                ?.attr("content")

        val description =
            doc.selectFirst("meta[property=og:description]")
                ?.attr("content")

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

        val doc = app.get(data).document

        val episodeList =
            doc.select(".episode-list .btn-player")

        val sourceId =
            episodeList.firstOrNull()
                ?.attr("data-source")
                ?: return false

        episodeList.forEach { item ->

            val requestBody = getRequestBody(
                item.attr("data-id"),
                sourceId
            )

            val response = app.post(
                ajaxUrl,
                requestBody = requestBody
            ).document

            val iframe =
                response.selectFirst("iframe")
                    ?.attr("src")
                    ?: return@forEach

            val finalUrl = iframe
                .replace("\\/", "/")
                .replace("\\\"", "")
                .substringBefore("?")

            loadExtractor(
                finalUrl,
                subtitleCallback,
                callback
            )
        }

        return true
    }
}
