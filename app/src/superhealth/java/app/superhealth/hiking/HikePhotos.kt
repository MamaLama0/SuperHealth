package app.superhealth.hiking

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import app.superhealth.modules.Modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Actual route photos are cached locally; no generated location photographs. */
object HikePhotos {
    const val MAX_BYTES = 3 * 1024 * 1024
    private val permits=Semaphore(3)
    private val http=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private fun key(route:HikeRoute)=MessageDigest.getInstance("SHA-256").digest(route.imageUrl.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun local(ctx:Context,route:HikeRoute)=File(ctx.filesDir,"hiking/photos/${key(route)}.jpg")
    fun cached(ctx:Context,route:HikeRoute):File? {
        if(route.imageUrl.isBlank()) return null
        return local(ctx,route).takeIf{it.isFile} ?: route.id.takeIf{it.matches(Regex("[a-zA-Z0-9_-]{1,80}"))}
            ?.let{Modules.file(Modules.HIKING,"photos/$it.jpg")}
    }
    fun pin(ctx:Context,route:HikeRoute) {
        if(route.imageUrl.isBlank())return
        val target=local(ctx,route);if(target.isFile)return
        val source=cached(ctx,route) ?: return
        // The photograph is optional: a full disk must not undo a saved route.
        runCatching{target.parentFile?.mkdirs();source.copyTo(target,overwrite=false)}
    }
    private fun allowed(raw:String):Boolean=raw.toHttpUrlOrNull()?.let{u->u.isHttps&&u.username.isEmpty()&&u.password.isEmpty()&&
        (u.host=="www.visorando.com" || u.host.endsWith(".cloudfront.net") || u.host.endsWith(".komoot.net") || u.host.endsWith(".komoot.com"))} ?: false
    suspend fun load(ctx:Context,route:HikeRoute):File?=withContext(Dispatchers.IO) {
        cached(ctx,route)?.let{return@withContext it}
        if(!allowed(route.imageUrl))return@withContext null
        permits.withPermit {
            cached(ctx,route)?.let{return@withPermit it}
            val bytes=http.newCall(Request.Builder().url(route.imageUrl).header("User-Agent","SuperHealth/3.1.2").build()).execute().use{r->
                check(r.isSuccessful && r.body.contentType()?.type=="image")
                val out=java.io.ByteArrayOutputStream()
                r.body.byteStream().use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;check(out.size()+n<=MAX_BYTES);out.write(b,0,n)}}
                out.toByteArray()
            }
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            check(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=4_000_000)
            val target=local(ctx,route);target.parentFile?.mkdirs()
            val atomic=AtomicFile(target);val output=atomic.startWrite()
            try{output.write(bytes);atomic.finishWrite(output)}catch(e:Exception){atomic.failWrite(output);throw e}
            val keep=Hiking.library.value.routes.filter{it.imageUrl.isNotBlank()}.map{local(ctx,it).name}.toSet()+target.name
            val files=target.parentFile!!.listFiles().orEmpty().filter{it.isFile}.sortedBy{it.lastModified()}
            var size=files.sumOf{it.length()}
            files.filter{it.name !in keep}.forEach{if(size>64L*1024*1024){val length=it.length();if(it.delete())size-=length}}
            target
        }
    }
    fun backupFiles(ctx:Context)=Hiking.library.value.routes.filter{it.imageUrl.isNotBlank()}.map{local(ctx,it)}.distinctBy{it.name}.filter{it.isFile}
}
