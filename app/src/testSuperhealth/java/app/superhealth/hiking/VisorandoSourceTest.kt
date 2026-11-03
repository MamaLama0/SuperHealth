package app.superhealth.hiking

import org.junit.Assert.*
import org.junit.Test

class VisorandoSourceTest {
    private val route = HikeRoute("visorando-481832", "Ancien nom", source = "https://www.visorando.com/randonnee-rando-des-trois-lacs/", points = emptyList())
    private val page = """
        <script type="application/ld+json">{"@type":"Product","name":"Les trois lacs","description":"Un circuit &amp; ses lacs."}</script>
        <meta property="og:image" content="https://www.visorando.com/images/inter/lacs.jpg">
        <div class="vr-walk-datasheet--dataset"><strong>Distance :</strong>11,53 km</div>
        <div class="vr-walk-datasheet--dataset"><strong>Durée moyenne :</strong>4h 30</div>
        <div class="vr-walk-datasheet--dataset"><strong>Difficulté :</strong>Moyenne</div>
        <div class="vr-walk-datasheet--dataset"><strong>Retour au départ :</strong>Oui</div>
        <div class="vr-walk-datasheet--dataset"><strong>Dénivelé positif :</strong>+ 425 m</div>
        <div class="vr-walk-datasheet--dataset"><strong>Dénivelé négatif :</strong>- 425 m</div>
        <div class="vr-walk-datasheet--dataset"><strong>Commune :</strong>Orbey (68370)</div>
        <section class="route_sheet_text_content"><header><h2>Description de la randonnée</h2></header>
          <article><p>Départ au lac Noir.</p><p>(1) Suivre le sentier Cornélius.</p></article></section>
        <section class="route_sheet_text_content"><header><h2>Informations pratiques</h2></header>
          <article><p>Prévoir des chaussures adaptées.</p></article></section>
    """.trimIndent()
    private val map = """{"geojson":{"features":[
        {"geometry":{"type":"Point","coordinates":[7.100189,48.112606]},"properties":{"a":0}},
        {"geometry":{"type":"Point","coordinates":[7.102,48.113]},"properties":{"a":0}},
        {"geometry":{"type":"Point","coordinates":[7.104,48.114]},"properties":{"a":1}}
    ]},"chartdata":{"data":[[0,955],[150,960],[330,965]]}}"""

    @Test fun extractsRealGeometryAndEditorialDetailsFromPublicFormats() {
        val result = VisorandoSource.parseDetail(route, page, map)
        assertEquals("Les trois lacs", result.name)
        assertEquals("Visorando", result.provider)
        assertEquals("Moyenne", result.difficulty)
        assertEquals(270, result.durationMinutes)
        assertEquals(11530.0, result.distanceMeters!!, .01)
        assertEquals(425.0, result.ascent!!, .01)
        assertEquals(425.0, result.descent!!, .01)
        assertTrue(result.loop)
        assertEquals(3, result.points.size)
        assertEquals(48.112606, result.points.first().lat, .000001)
        assertEquals(955.0, result.points.first().ele!!, .01)
        assertEquals(2, result.directions.size)
        assertTrue(result.description.contains("chaussures adaptées"))
        assertTrue(result.imageUrl.contains("lacs.jpg"))
    }

    @Test fun neverAssignsAnUnmatchedAltitudeProfileOrJoinsSeparateTraces() {
        val separated = """{"geojson":{"features":[
            {"geometry":{"type":"Point","coordinates":[7.1,48.1]},"properties":{"a":0}},
            {"geometry":{"type":"Point","coordinates":[7.102,48.101]},"properties":{"a":1}},
            {"geometry":{"type":"Point","coordinates":[8.1,49.1]},"properties":{"a":0}},
            {"geometry":{"type":"Point","coordinates":[8.102,49.101]},"properties":{"a":1}}
        ]},"chartdata":{"data":[[0,100]]}}"""
        val result = VisorandoSource.parseDetail(route, page, separated)
        assertTrue(result.points.all { it.ele == null })
        assertEquals(listOf(0,0,1,1), result.points.map { it.segment })
        assertTrue(HikeGeometry.length(result.points) in 200.0..600.0)
    }

    @Test fun ignoresNonOfficialLinksAndDeduplicatesRouteCards() {
        val html = """<div class="vr-card--rando"><a id="blockRando_481832"></a>
            <a class="card--link" href="https://www.visorando.com/randonnee-rando-des-trois-lacs/" title="Les trois lacs"></a>
            <a class="card--link" href="https://www.visorando.com/randonnee-rando-des-trois-lacs/" title="Doublon"></a>
            <a class="card--link" href="https://invalid.example/randonnee-bad/" title="Bad"></a></div>"""
        val results = VisorandoSource.links(html, "Alsace")
        assertEquals(1, results.size)
        assertEquals("visorando-481832", results.single().id)
        assertEquals("Alsace", results.single().region)
    }
}
