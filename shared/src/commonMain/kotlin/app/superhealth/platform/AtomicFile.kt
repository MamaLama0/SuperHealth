package app.superhealth.platform

/** Écriture transactionnelle : la version précédente reste lisible jusqu'au renommage atomique. */
class AtomicFile(private val file: File) {
    private val pending get() = File(file.path + ".new")
    fun openRead() = InputStream(file.readBytes())
    fun startWrite(): OutputStream { file.parentFile?.mkdirs(); return object : OutputStream() { override fun close() { pending.writeBytes(bytes()) } } }
    fun finishWrite(out: OutputStream) { out.close(); check(pending.renameTo(file)) { "Écriture atomique impossible" } }
    fun failWrite(out: OutputStream) { runCatching { out.close() }; pending.delete() }
}
