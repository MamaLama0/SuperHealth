package app.superhealth.social

/** Alert descriptions deliberately omit message text and media. */
data class SpaceAlert(val id: String, val circle: String, val post: String?, val category: String)
fun spaceAlerts(before: SpaceStore, after: SpaceStore, now: Long): List<SpaceAlert> {
    val invites=after.invitations.filter { next->before.invitations.none{it.circle==next.circle} }
        .map { SpaceAlert("invite:"+it.circle,it.circle,null,"INVITE") }
    if(before.own==null || !before.notificationBaseline)return invites
    val known=before.records.map{Triple(it.circle,it.author.id,it.action.id)}.toSet()
    return invites+after.records.filter { record->
        record.circle !in after.silentCircles && record.author.key!=after.own?.key && !record.action.deleted &&
            record.action.kind in setOf("POST","REPLY","REACTION","CHALLENGE") &&
            record.action.created>now-86400000 && Triple(record.circle,record.author.id,record.action.id) !in known
    }.map { SpaceAlert(it.author.id+":"+it.action.id,it.circle,if(it.action.kind in setOf("REPLY","REACTION"))it.action.parent else it.action.id,it.action.kind) }
}
