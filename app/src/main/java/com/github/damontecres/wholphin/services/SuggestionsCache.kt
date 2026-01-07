package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedSuggestions(
    val items: List<BaseItem>,
    val timestamp: Long,
)

@Singleton
class SuggestionsCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val mutex = Mutex()
        private val memoryCache = ConcurrentHashMap<String, CachedSuggestions>()

        private fun cacheKey(
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): String = "${libraryId}_${itemKind.serialName}"

        private fun cacheFile(
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): File {
            val cacheDir = File(context.cacheDir, "suggestions")
            cacheDir.mkdirs()
            return File(cacheDir, "${libraryId}_${itemKind.serialName}.json")
        }

        suspend fun get(
            libraryId: UUID,
            itemKind: BaseItemKind,
        ): CachedSuggestions? {
            val key = cacheKey(libraryId, itemKind)
            memoryCache[key]?.let { return it }

            return withContext(Dispatchers.IO) {
                try {
                    val file = cacheFile(libraryId, itemKind)
                    if (file.exists()) {
                        val cached = json.decodeFromString<CachedSuggestions>(file.readText())
                        memoryCache[key] = cached
                        cached
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Failed to read suggestions cache")
                    null
                }
            }
        }

        suspend fun put(
            libraryId: UUID,
            itemKind: BaseItemKind,
            items: List<BaseItem>,
        ) {
            val key = cacheKey(libraryId, itemKind)
            val cached = CachedSuggestions(items, System.currentTimeMillis())
            memoryCache[key] = cached

            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        cacheFile(libraryId, itemKind).writeText(json.encodeToString(cached))
                    } catch (ex: Exception) {
                        Timber.w(ex, "Failed to write suggestions cache")
                    }
                }
            }
        }

        suspend fun clear() {
            memoryCache.clear()
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    File(context.cacheDir, "suggestions").deleteRecursively()
                }
            }
        }
    }
