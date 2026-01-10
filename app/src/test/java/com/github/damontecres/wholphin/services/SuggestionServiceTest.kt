package com.github.damontecres.wholphin.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SuggestionServiceTest {
    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>()
    private val mockCache = mockk<SuggestionsCache>()
    private val mockWorkManager = mockk<WorkManager>()

    private fun createService() = SuggestionService(
        api = mockApi,
        serverRepository = mockServerRepository,
        cache = mockCache,
        workManager = mockWorkManager,
    )

    private fun mockUser(id: UUID = UUID.randomUUID()): JellyfinUser =
        JellyfinUser(
            id = id,
            name = "TestUser",
            serverId = UUID.randomUUID(),
            accessToken = "token",
        )

    private fun mockWorkInfo(state: WorkInfo.State): WorkInfo =
        mockk<WorkInfo> { every { this@mockk.state } returns state }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenNoUserLoggedIn() = runBlocking {
        val currentUser = MutableLiveData<JellyfinUser?>(null)
        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        val service = createService()
        val result = service.getSuggestionsFlow(UUID.randomUUID(), BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Empty, result)
    }

    @Test
    fun getSuggestionsFlow_returnsLoading_whenCacheEmptyAndWorkerRunning() = runBlocking {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(
            listOf(mockWorkInfo(WorkInfo.State.RUNNING))
        )

        val service = createService()
        val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Loading, result)
    }

    @Test
    fun getSuggestionsFlow_returnsLoading_whenCacheEmptyAndWorkerEnqueued() = runBlocking {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(
            listOf(mockWorkInfo(WorkInfo.State.ENQUEUED))
        )

        val service = createService()
        val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Loading, result)
    }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenCacheEmptyAndWorkerSucceeded() = runBlocking {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(
            listOf(mockWorkInfo(WorkInfo.State.SUCCEEDED))
        )

        val service = createService()
        val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Empty, result)
    }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenCacheEmptyAndWorkerFailed() = runBlocking {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(
            listOf(mockWorkInfo(WorkInfo.State.FAILED))
        )

        val service = createService()
        val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Empty, result)
    }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenCacheEmptyAndWorkerCancelled() = runBlocking {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(
            listOf(mockWorkInfo(WorkInfo.State.CANCELLED))
        )

        val service = createService()
        val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Empty, result)
    }

    @Test
    fun getSuggestionsFlow_returnsEmpty_whenCacheEmptyAndNoWorkInfo() = runBlocking {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, parentId, BaseItemKind.MOVIE) } returns null
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        val service = createService()
        val result = service.getSuggestionsFlow(parentId, BaseItemKind.MOVIE).first()

        assertEquals(SuggestionsResource.Empty, result)
    }

    @Test
    fun getSuggestionsFlow_usesCorrectLibraryId() = runBlocking {
        val userId = UUID.randomUUID()
        val movieLibraryId = UUID.randomUUID()
        val tvLibraryId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, movieLibraryId, BaseItemKind.MOVIE) } returns null
        coEvery { mockCache.get(userId, tvLibraryId, BaseItemKind.MOVIE) } returns
            CachedSuggestions(listOf("item-1"))
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        val service = createService()

        val movieResult = service.getSuggestionsFlow(movieLibraryId, BaseItemKind.MOVIE).first()
        assertEquals(SuggestionsResource.Empty, movieResult)

        // TV library has cached items - would return Success if API was mocked
        // This test verifies the correct libraryId is passed to cache.get()
    }

    @Test
    fun getSuggestionsFlow_usesCorrectItemKind() = runBlocking {
        val userId = UUID.randomUUID()
        val libraryId = UUID.randomUUID()
        val currentUser = MutableLiveData<JellyfinUser?>(mockUser(userId))

        every { mockServerRepository.currentUser } returns currentUser
        every { mockCache.cacheVersion } returns MutableStateFlow(0L)
        coEvery { mockCache.get(userId, libraryId, BaseItemKind.MOVIE) } returns null
        coEvery { mockCache.get(userId, libraryId, BaseItemKind.SERIES) } returns
            CachedSuggestions(listOf("series-1"))
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        val service = createService()

        val movieResult = service.getSuggestionsFlow(libraryId, BaseItemKind.MOVIE).first()
        assertEquals(SuggestionsResource.Empty, movieResult)

        // SERIES has cached items - verifies correct itemKind is passed to cache.get()
    }
}
