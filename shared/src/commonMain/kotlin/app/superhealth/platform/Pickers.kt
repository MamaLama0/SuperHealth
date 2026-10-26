package app.superhealth.platform

import androidx.compose.runtime.Composable

/*
 * Sélecteurs du système, à la place des ActivityResultContracts d'Android : fichiers (Fichiers / iCloud Drive),
 * photos (photothèque), appareil photo. Chaque fonction rend le lanceur à appeler depuis un clic.
 */

class PickedFile(val name: String, val bytes: ByteArray)

/** Ouvre un ou plusieurs fichiers ; [onResult] reçoit une liste vide si l'utilisateur annule. */
@Composable
expect fun rememberFilePicker(multiple: Boolean = false, onResult: (List<PickedFile>) -> Unit): () -> Unit

/** Choisit une photo de la photothèque, rendue en JPEG ; null si annulé. */
@Composable
expect fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit

/** Prend une photo, rendue en JPEG ; null si annulé ou appareil indisponible. */
@Composable
expect fun rememberCamera(onResult: (ByteArray?) -> Unit): () -> Unit

/** Enregistre un fichier : feuille de partage du système (« Enregistrer dans Fichiers », AirDrop, Mail…). */
fun saveFile(name: String, mime: String, bytes: ByteArray) = shareFile(name, mime, bytes)

/** Lecteur de code-barres (EAN/UPC) plein écran ; null si annulé. */
@Composable
expect fun rememberBarcodeScanner(onResult: (String?) -> Unit): () -> Unit

/** Image JPEG réduite pour qu'aucun côté ne dépasse [maxSide] (photos envoyées à l'IA, photos de progression). */
expect fun downscaleJpeg(bytes: ByteArray, maxSide: Int, quality: Int = 85): ByteArray
