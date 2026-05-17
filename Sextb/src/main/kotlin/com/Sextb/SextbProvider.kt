package com.Sextb

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody

class SextbProvider : MainAPI() {
    override var mainUrl              = "https://sextb.net"
    override var name                 = "Sextb"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    private val ajaxUrl = "$mainUrl/ajax/player"

    override val mainPage = mainPageOf(
        "/amateur" to "Amateur",
        "/censored" to "Censored",
        "/uncensored" to "Uncensord",
        "/subtitle" to "English Subtitled"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}/pg-$page").document
        val responseList  = document.select(".tray-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = false),hasNext = true)
    }

    private fun getRequestBody (episode: String, filmId: String) : FormBody {
        return FormBody.Builder()
            .addEncoded("episode", episode)
            .addEncoded("filmId", filmId)
            .build()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select(".tray-item-title").text()
        val href = mainUrl + this.select("a:nth-of-type(1)").attr("href")
        val posterUrl = this.selectFirst(".tray-item-thumbnail")?.attr("data-src")
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val document = app.get("$mainUrl/search/${query.replace(" ", "-")}/pg-$page").document
        val results = document.select(".tray-item").mapNotNull { it.toSearchResult() }
        val hasNext = if(results.isEmpty()) false else true
        return newSearchResponseList(results, hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString().replace("| PornHoarder.tv","")
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
    
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

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

    val episodeList = document.select(".episode-list .btn-player")

    episodeList.forEach { item ->

        val episodeId = item.attr("data-id")

        val requestBody = getRequestBody(episodeId, sourceId)

        val response = app.post(
            ajaxUrl,
            requestBody = requestBody,
            headers = mapOf(
                "Referer" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).document

        // lấy toàn bộ iframe/server
        val iframes = response.select("iframe")

        iframes.forEachIndexed { index, iframe ->

            val iframeSrc = iframe.attr("src")
                .replace("\\/", "/")
                .replace("\\\"", "")
                .substringBefore("?")

            if (iframeSrc.isNotBlank()) {

                loadExtractor(
                    iframeSrc,
                    subtitleCallback
                ) { link ->

                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "${name} Server ${index + 1}",
                            url = link.url,
                            referer = mainUrl,
                            quality = link.quality,
                            type = link.type,
                            headers = link.headers
                        )
                    )
                }
            }
        }
    }

    return true
  }
}
