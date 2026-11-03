package app.superhealth

import app.superhealth.platform.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.*

/** Le transport conserve les capacités sur leur origine et borne les réponses avant de les décoder. */
class SocialTransportTest {
    private fun serve(block: (String,SocialHttpClient)->Unit) {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/plain") { e ->
            val bytes="réponse UTF-8".encodeToByteArray();e.responseHeaders.add("ETag","tag-3");e.sendResponseHeaders(200,bytes.size.toLong());e.responseBody.use {it.write(bytes)}
        }
        server.createContext("/redirect") { e ->e.responseHeaders.add("Location","/plain");e.sendResponseHeaders(302,-1);e.close()}
        server.createContext("/large") { e ->e.sendResponseHeaders(200,300_000);runCatching {e.responseBody.use {it.write(ByteArray(300_000))}};e.close()}
        server.createContext("/echo") {e ->
            val text=e.requestMethod+":"+e.requestHeaders.getFirst("X-Write-Token")+":"+e.requestBody.readBytes().decodeToString()
            val bytes=text.encodeToByteArray();e.sendResponseHeaders(200,bytes.size.toLong());e.responseBody.use {it.write(bytes)}
        }
        server.start()
        val client=SocialHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
        try {block("http://127.0.0.1:${server.address.port}",client)} finally {client.close();server.stop(0)}
    }
    @Test fun responseAndHeadersRemainIntact()=serve {base,http ->
        http.newCall(SocialRequest.Builder().url("$base/plain").get().build()).execute().use {
            assertEquals(200,it.code);assertEquals("tag-3",it.header("etag"));assertEquals("réponse UTF-8",it.body.bytes.decodeToString())
        }
    }
    @Test fun neverFollowsRedirects()=serve {base,http ->
        assertEquals(302,http.newCall(SocialRequest.Builder().url("$base/redirect").get().build()).execute().code)
    }
    @Test fun refusesOversizedStream()=serve {base,http ->
        assertFails {http.newCall(SocialRequest.Builder().url("$base/large").get().build()).execute()}
    }
    @Test fun publicationPreservesMethodOwnerCapabilityAndPayload()=serve {base,http ->
        val request=SocialRequest.Builder().url("$base/echo").header("X-Write-Token","owner").put("chiffré".toRequestBody("application/json")).build()
        assertEquals("PUT:owner:chiffré",http.newCall(request).execute().body.bytes.decodeToString())
    }
}
