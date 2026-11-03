package app.superhealth.hiking

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class KomootSourceTest {
    // Minimized real list/detail response for editorial tour e2579390572. Coordinates, id and metrics
    // are retained; the encoded public path query is replaced with a fixture value. No account data.
    private fun editorial(detail: Boolean = false): JsonObject {
        val geometry = """[{"lat":46.574879,"lng":11.669959,"alt":1221.6},{"lat":46.565639,"lng":11.677658,"alt":1514.6},{"lat":46.555152,"lng":11.672023,"alt":1803.4},{"lat":46.574879,"lng":11.669959,"alt":1221.6}]"""
        val coordinates = if (detail) "\"coordinates\":{\"items\":$geometry}" else "\"tour_line\":{\"geometry\":$geometry}"
        return Json.parseToJsonElement("""{
          "id":"e2579390572", "name":"Promenade culturelle du village d'Ortisei", "sport":"hike",
          "query":"fixture-generated-path==", "distance":9535.94734990506,"duration":15259,
          "elevation_up":657.2751045207947,"elevation_down":657.2751045207947,
          "difficulty":{"grade":"moderate"},"_embedded":{$coordinates}
        }""").jsonObject
    }

    // Shape observed from the authorized APK's /v007/discover_tours/from_location/ response.
    private fun item() = Json.parseToJsonElement("""{
      "id":"39305063", "name":"Boucle du château", "sport":"hike", "query":"generated-path-query",
      "distance":820.0, "duration":1800, "elevation_up":32, "elevation_down":31,
      "difficulty":{"grade":"moderate"}, "summary":{"surfaces":[{"type":"sf#nature","amount":0.7},{"type":"sf#asphalt","amount":0.3}]},
      "_embedded":{"tour_line":{"geometry":[{"lat":48.43,"lng":7.48,"alt":160},{"lat":48.435,"lng":7.483,"alt":190},{"lat":48.43,"lng":7.48,"alt":160}]},
      "cover_images":{"_embedded":{"items":[{"src":"https://d2exd72xrrp1s7.cloudfront.net/sample.jpg?width={width}&height={height}&crop={crop}","_embedded":{"creator":{"display_name":"Photo auteur"}}}]}}}
    }""").jsonObject

    @Test fun preservesGeneratedGeometryAndRichMetadata() {
        val route = KomootSource.parseRoute(item(), "Alsace")!!
        assertEquals("komoot-39305063-957fda9ef0ba", route.id)
        assertEquals("Komoot", route.provider)
        assertEquals("generated-path-query", route.providerQuery)
        assertEquals("Alsace", route.region)
        assertEquals(3, route.points.size)
        assertEquals(190.0, route.points[1].ele!!, 0.0)
        assertTrue(route.loop)
        assertEquals(30, route.durationMinutes)
        assertEquals(820.0, route.distanceMeters!!, 0.0)
        assertEquals(31.0, route.descent!!, 0.0)
        assertEquals("moderate", route.difficulty)
        assertEquals(0.7, route.surfaces.getValue("nature"), 0.0)
        assertTrue(route.imageUrl.endsWith("width=960&height=600&crop=true"))
        assertEquals("Photo auteur · Komoot", route.imageCredit)
    }

    @Test fun rejectsBrokenGeometryInsteadOfJoiningAcrossMissingPoints() {
        val bad = item().toString().replace("48.435", "148.435")
        assertNull(KomootSource.parseRoute(Json.parseToJsonElement(bad).jsonObject, "Alsace"))
        assertNull(KomootSource.parseRoute(JsonObject(item() - "_embedded"), "Alsace"))
    }

    @Test fun detailUsesCoordinatesAndKeepsManeuvers() {
        val old = item(); val embedded = old.getValue("_embedded").jsonObject
        val coordinates = Json.parseToJsonElement("""{"items":[{"lat":48.43,"lng":7.48,"alt":160},{"lat":48.436,"lng":7.48,"alt":180}]}""")
        val directions = Json.parseToJsonElement("""{"items":[{"type":"TL","street_name":"Rue du château"},{"type":"TS","street_name":"Sentier"}]}""")
        val route = KomootSource.parseRoute(JsonObject(old + ("_embedded" to JsonObject(embedded + mapOf("coordinates" to coordinates, "directions" to directions)))), "Alsace")!!
        assertEquals(2, route.points.size)
        assertEquals(listOf("← Rue du château", "↑ Sentier"), route.directions)
    }

    @Test fun rejectsRemoteImagesOutsideTheObservedProviders() {
        val text = item().toString().replace("https://d2exd72xrrp1s7.cloudfront.net", "http://127.0.0.1")
        val route = KomootSource.parseRoute(Json.parseToJsonElement(text).jsonObject, "Alsace")!!
        assertEquals("", route.imageUrl)
    }

    @Test fun searchIsBoundedAndDeduplicatesIds() {
        val root = buildJsonObject { putJsonObject("_embedded") { putJsonArray("items") { repeat(30) { add(item()) } } } }
        assertEquals(1, KomootSource.parseSearch(root, "Alsace").size)
    }

    @Test fun acceptsActualEditorialIdsInBothListAndDetailSchemas() {
        val root = buildJsonObject { putJsonObject("_embedded") { putJsonArray("items") { add(editorial()) } } }
        val listed = KomootSource.parseSearch(root, "Dolomites").single()
        val detailed = KomootSource.parseRoute(editorial(detail = true), "Dolomites")!!
        assertEquals("komoot-e2579390572-8d42a45ce385", listed.id)
        assertEquals(listed.id, detailed.id)
        assertEquals(4, detailed.points.size)
        assertEquals(1803.4, detailed.points[2].ele!!, 0.0)
        assertEquals(254, detailed.durationMinutes)
    }

    @Test fun detailEndpointSupportsBothObservedIdFormsAndKeepsTheExactQuery() {
        val query = "exact-path-query=+/_-=="
        for (id in listOf("39305063", "e2579390572")) {
            val route = KomootSource.parseRoute(JsonObject(editorial() + mapOf("id" to JsonPrimitive(id), "query" to JsonPrimitive(query))), "Dolomites")!!
            val url = KomootSource.detailUrl(route)!!
            assertEquals("/v007/discover_tours/$id", url.encodedPath)
            assertEquals(query, url.queryParameter("query"))
            assertEquals(url, KomootSource.detailUrl(route.copy(id = "komoot-$id")))
        }
        val route = KomootSource.parseRoute(editorial(), "Dolomites")!!
        assertNull(KomootSource.detailUrl(route.copy(id = "komoot-../2579390572")))
        assertNull(KomootSource.detailUrl(route.copy(id = "komoot-other2579390572")))
        assertNull(KomootSource.detailUrl(route.copy(id = "komoot-e2579390572-not-a-hash")))
        assertNull(KomootSource.detailUrl(route.copy(providerQuery = "q".repeat(2001))))
    }

    @Test fun enrichmentKeepsTheSavedIdentityPhotoKeySourceAndOriginalPathQuery() {
        val source = "https://www.komoot.com/smarttour/e2579390572?ref=stored-route"
        val original = JsonObject(editorial() + ("query" to JsonPrimitive("original-query=+==")))
        val route = KomootSource.parseRoute(original, "Dolomites")!!.copy(source = source,
            features = listOf(MapFeature("path", listOf(HikePoint(46.57, 11.67)))))
        // The provider may normalize its identifiers/path query; local references must remain stable.
        val normalized = JsonObject(editorial(detail = true) + mapOf("id" to JsonPrimitive("2579390572"), "query" to JsonPrimitive("normalized-query")))
        val enriched = KomootSource.parseDetail(route, normalized)
        assertEquals(route.id, enriched.id)
        assertEquals("photos/${route.id}.jpg", "photos/${enriched.id}.jpg")
        assertEquals(source, enriched.source)
        assertEquals("original-query=+==", enriched.providerQuery)
        assertEquals(route.features, enriched.features)
        assertEquals(4, enriched.points.size)
    }

    @Test fun generatedVariantsHaveDistinctLocalIdsAndIdenticalQueriesRemainStable() {
        val first = JsonObject(editorial() + ("query" to JsonPrimitive("first-generated-query")))
        val second = JsonObject(editorial() + ("query" to JsonPrimitive("second-generated-query")))
        val a = KomootSource.parseRoute(first, "Dolomites")!!
        val b = KomootSource.parseRoute(second, "Dolomites")!!
        assertEquals("komoot-e2579390572-dbfdd6d89280", a.id)
        assertEquals("komoot-e2579390572-a65bdd924739", b.id)
        assertNotEquals(a.id, b.id)
        assertEquals(a.id, KomootSource.parseRoute(first, "Another display region")!!.id)
        assertEquals(a.source, b.source)
        assertEquals("first-generated-query", KomootSource.detailUrl(a)!!.queryParameter("query"))
        assertEquals("second-generated-query", KomootSource.detailUrl(b)!!.queryParameter("query"))
        assertEquals("/v007/discover_tours/e2579390572", KomootSource.detailUrl(a)!!.encodedPath)
        val root = buildJsonObject { putJsonObject("_embedded") { putJsonArray("items") { add(first); add(second); add(first) } } }
        assertEquals(listOf(a.id, b.id), KomootSource.parseSearch(root, "Dolomites").map { it.id })
    }

    @Test fun missingGeneratedQueryRetainsTheLegacyLocalId() {
        val route = KomootSource.parseRoute(JsonObject(editorial() - "query"), "Dolomites")!!
        assertEquals("komoot-e2579390572", route.id)
        assertEquals("", route.providerQuery)
        assertNull(KomootSource.detailUrl(route))
    }

    @Test fun rejectsOverlongOpaqueQueriesRatherThanTruncatingTheirPathIdentity() {
        val item = JsonObject(editorial() + ("query" to JsonPrimitive("q".repeat(2001))))
        assertNull(KomootSource.parseRoute(item, "Dolomites"))
        val root = buildJsonObject { putJsonObject("_embedded") { putJsonArray("items") { add(item) } } }
        assertTrue(KomootSource.parseSearch(root, "Dolomites").isEmpty())
    }
}
