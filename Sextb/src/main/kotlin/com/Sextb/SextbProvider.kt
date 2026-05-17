package com.Sextb

import org.jsoup.nodes.Element
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody

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

    // ================= MAIN PAGE =================
    override val mainPage = mainPageOf(
        "/amateur" to "Amateur",
        "/censored" to "Censored",
        "/uncensored" to "Uncensored",
        "/subtitle" to "English Subtitled"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}/pg-$page").document

        val list = doc.select(".tray-item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, list, isHorizontalImages = false),
            hasNext = list.isNotEmpty()
        )
    }

    // ================= SEARCH =================
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val doc = app.get("$mainUrl/search/${query.replace(" ", "-")}/pg-$page").document

        val list = doc.select(".tray-item")
            .mapNotNull { it.toSearchResult() }

        return newSearchResponseList(list, list.isNotEmpty())
    }

    // ================= LOAD =================
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.replace("| PornHoarder.tv", "")
            ?.trim()
            ?: "Unknown"

        val poster = doc.selectFirst("meta[property=og:image]")
            ?.attr("content")

        val desc = doc.selectFirst("meta[property=og:description]")
            ?.attr("content")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = desc
        }
    }

    // ================= LINKS (FIXED MULTI-LAYER) =================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        val sourceId = doc.selectFirst(".episode-list .btn-player")
            ?.attr("data-source")
            ?: return false

        val episode = doc.select(".episode-list .btn-player").firstOrNull()
            ?: return false

        val body = FormBody.Builder()
            .add("episode", episode.attr("data-id"))
            .add("filmId", sourceId)
            .build()

        val ajaxResponse = app.post(
            ajaxUrl,
            requestBody = body,
            headers = mapOf(
                "Referer" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).text

        val iframeDoc = Jsoup.parse(ajaxResponse)

        val iframes = iframeDoc.select("iframe")

        iframes.forEachIndexed { index, iframe ->

            val iframeUrl = cleanUrl(iframe.attr("src"))
                ?: return@forEachIndexed

            // ================= LAYER 1 =================
            resolveFinalSources(
                iframeUrl,
                subtitleCallback,
                callback,
                depth = 0
            )
        }

        return true
    }

    // ================= CORE RESOLVER =================
    private suspend fun resolveFinalSources(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        depth: Int
    ) {
        if (depth > 2) return

        val res = app.get(url, referer = mainUrl).text
        val doc = Jsoup.parse(res)

        // 1) direct video/source
        val directSources = doc.select("video source, source, video")

        if (directSources.isNotEmpty()) {
            directSources.forEach {
                val src = cleanUrl(it.attr("src")) ?: return@forEach

                loadExtractor(src, url, subtitleCallback) { link ->
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name Stream",
                            url = link.url,
                            referer = url,
                            quality = link.quality,
                            type = link.type,
                            headers = link.headers
                        )
                    )
                }
            }
            return
        }

        // 2) iframe tiếp (TB / SW / DD / FL / ST / US / PP nằm ở đây)
        val innerIframes = doc.select("iframe")

        if (innerIframes.isNotEmpty()) {
            innerIframes.forEach {
                val next = cleanUrl(it.attr("src")) ?: return@forEach

                resolveFinalSources(
                    next,
                    subtitleCallback,
                    callback,
                    depth + 1
                )
            }
            return
        }

        // 3) fallback extractor (quan trọng)
        loadExtractor(url, mainUrl, subtitleCallback) { link ->
            callback(
                ExtractorLink(
                    source = name,
                    name = "$name Fallback",
                    url = link.url,
                    referer = url,
                    quality = link.quality,
                    type = link.type,
                    headers = link.headers
                )
            )
        }
    }

    // ================= HELPERS =================
    private fun Element.toSearchResult(): SearchResponse {
        val title = select(".tray-item-title").text()
        val href = mainUrl + select("a:nth-of-type(1)").attr("href")
        val poster = selectFirst(".tray-item-thumbnail")?.attr("data-src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    private fun cleanUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url
            .replace("\\/", "/")
            .replace("\\\"", "")
            .substringBefore("?")
    }
}
