package com.showraw.android.youtube

import java.io.File

enum class YouTubePrivacy { PUBLIC, UNLISTED, PRIVATE }

data class YouTubeMetadata(
    val title: String,
    val description: String = "Gravado com ShowRaw — áudio profissional em tempo real.",
    val privacy: YouTubePrivacy = YouTubePrivacy.UNLISTED,
)

/**
 * Upload para o YouTube via Data API v3.
 * Usa upload multipart resumível para suportar conexões instáveis.
 * Auth: OAuth 2.0 com refresh token persistido em EncryptedSharedPreferences.
 */
class YouTubeUploader {

    fun isAuthenticated(): Boolean {
        // TODO: verificar refresh token em EncryptedSharedPreferences
        return false
    }

    suspend fun authenticate(context: android.content.Context) {
        // TODO: OAuth 2.0 flow — abrir CustomTabsIntent com URL de autorização Google
    }

    suspend fun upload(
        videoFile: File,
        metadata: YouTubeMetadata,
        onProgress: (Long, Long) -> Unit, // bytesUploaded, totalBytes
    ): String? { // retorna YouTube video ID ou null em caso de erro
        // TODO: implementar upload multipart resumível
        return null
    }
}
