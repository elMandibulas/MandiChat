package com.mandi.chat.data

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Databases
import io.appwrite.services.Storage

object AppwriteProvider {
    const val ENDPOINT = "https://fra.cloud.appwrite.io/v1"
    const val PROJECT_ID = "6aaa64e40001df66f2df"

    // Production TablesDB - COPIA EL DATABASE ID DE SETTINGS DE TU Production TablesDB
    const val DATABASE_ID = "6aaa6527003d9205596c" // <- pon el ID real de Production si este es el viejo
    
    const val COLLECTION_PROFILES = "6aaa654e00153b50ee66" // <- ID real de profiles en Production
    const val COLLECTION_MATCHES = "6aaa6dff003a585e4c12" // el que ya tenias bien
    const val COLLECTION_MESSAGES = "6aaa6e740031fb8b2a50" // el de tu captura
    const val COLLECTION_LIKES = "6aaa68e7002c3de69642"
    // ESTO ES LO QUE TE FALTA Y DA ERROR
    const val BUCKET_AVATARS = "6aaa6ee2000b30b5dd78"
    const val DEFAULT_AVATAR_ID = "6aafc3a80007655f1b94"

    private lateinit var appContext: Context

    val client by lazy {
        Client(appContext)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
    }

    val databases by lazy { Databases(client) }
    val storage by lazy { Storage(client) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}