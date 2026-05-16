package com.Sextb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class SextbPlugin : Plugin() {

    override fun load() {

        registerMainAPI(SextbProvider())

        registerExtractorAPI(StreamTape())

        // registerExtractorAPI(Stbturbo())
        // registerExtractorAPI(Wishonly())
    }
}
