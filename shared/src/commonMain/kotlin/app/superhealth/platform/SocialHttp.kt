package app.superhealth.platform

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking

/** Transport du relais : aucune redirection, réponse bornée avant allocation, délais explicites. */
class SocialHttpClient private constructor(private val connect: Long, private val read: Long, private val total: Long): AutoCloseable {
    private val client = HttpClient { install(HttpTimeout); followRedirects = false; expectSuccess = false }
    class Builder {
        private var connect=8000L; private var read=12000L; private var total=15000L
        fun connectTimeout(value: Long, unit: TimeUnit) = apply { connect=unit.toMillis(value) }
        fun readTimeout(value: Long, unit: TimeUnit) = apply { read=unit.toMillis(value) }
        fun callTimeout(value: Long, unit: TimeUnit) = apply { total=unit.toMillis(value) }
        fun followRedirects(value: Boolean) = apply { require(!value) }
        fun followSslRedirects(value: Boolean) = apply { require(!value) }
        fun build()=SocialHttpClient(connect,read,total)
    }
    override fun close() { client.close() }
    fun newCall(request: SocialRequest)=Call(request)
    inner class Call(private val request: SocialRequest) {
        fun execute(): SocialResponse = runBlocking {
            client.prepareRequest(request.url) {
                method=HttpMethod.parse(request.method)
                headers { request.headers.forEach { (k,v)->append(k,v) }; request.body?.let { append("Content-Type",it.type) } }
                timeout { connectTimeoutMillis=connect;socketTimeoutMillis=read;requestTimeoutMillis=total }
                request.body?.let { setBody(it.bytes) }
            }
            .execute { response ->
            val channel=response.bodyAsChannel();val buffer=ByteArray(8192);val out=ByteArrayOutputStream()
            try {
                while(true) {
                    val n=channel.readAvailable(buffer,0,buffer.size)
                    if(n<0)break
                    check(out.size()+n<=16*16384+2048){"Réponse trop volumineuse"}
                    out.write(buffer,0,n)
                }
            } finally { channel.cancel(null) }
            SocialResponse(response.status.value,response.headers.entries().associate { it.key to it.value.joinToString(",") },out.toByteArray())
            }
        }
    }
}
data class SocialRequest(val url: String,val method: String,val headers: Map<String,String>,val body: SocialBody?) {
    class Builder {
        private var url="";private var method="GET";private val headers=mutableMapOf<String,String>();private var body: SocialBody?=null
        fun url(value: String)=apply{url=value}
        fun header(key: String,value: String)=apply{headers[key]=value}
        fun get()=apply{method="GET";body=null}
        fun put(value: SocialBody)=apply{method="PUT";body=value}
        fun post(value: SocialBody)=apply{method="POST";body=value}
        fun delete()=apply{method="DELETE";body=null}
        fun build()=SocialRequest(url,method,headers.toMap(),body)
    }
}
data class SocialBody(val bytes: ByteArray,val type: String) {
    fun byteStream()=InputStream(bytes)
}
class SocialResponse(val code: Int,private val headers: Map<String,String>,data: ByteArray):AutoCloseable {
    val body=SocialBody(data,"")
    val isSuccessful get()=code in 200..299
    fun header(key: String)=headers.entries.firstOrNull{it.key.equals(key,true)}?.value
    override fun close(){}
}
fun String.toMediaType()=this
fun String.toRequestBody(type: String)=SocialBody(encodeToByteArray(),type)
class SocialUrl private constructor(private val raw: String,private val url: Url) {
    val isHttps get()=url.protocol.name=="https"
    val username get()=url.user.orEmpty()
    val password get()=url.password.orEmpty()
    val query get()=raw.takeIf{'?' in it}?.substringAfter('?')?.substringBefore('#')
    val fragment get()=raw.takeIf{'#' in it}?.substringAfter('#')
    val encodedPath get()=url.encodedPath.ifEmpty{"/"}
    companion object {
        fun parse(raw: String):SocialUrl?=runCatching{val u=Url(raw);require(u.host.isNotBlank()&&u.protocol.name in setOf("http","https"));SocialUrl(raw,u)}.getOrNull()
    }
}
fun String.toHttpUrlOrNull()=SocialUrl.parse(this)
