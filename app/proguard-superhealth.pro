# SuperHealth (flavor superhealth) : modèles sérialisés en JSON (kotlinx.serialization) et entités Room.
-keep,includedescriptorclasses class app.superhealth.**$$serializer { *; }
-keepclassmembers class app.superhealth.** { *** Companion; }
-keepclasseswithmembers class app.superhealth.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class app.superhealth.** { *; }
# Enums lus par nom (valueOf) dans les réglages et sauvegardes
-keepclassmembers enum app.superhealth.** { *; }
# Récepteurs, widgets et workers instanciés par le système
-keep class app.superhealth.** extends android.content.BroadcastReceiver
-keep class app.superhealth.** extends androidx.work.ListenableWorker { <init>(...); }
-keep class app.superhealth.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# Widgets : Glance instancie les ActionCallback par leur nom de classe (bouton « + 250 ml »).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
