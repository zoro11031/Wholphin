package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.Operation
import androidx.work.WorkManager
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsSchedulerServiceTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockActivity: AppCompatActivity
    private lateinit var mockServerRepository: ServerRepository
    private lateinit var mockCache: SuggestionsCache
    private lateinit var mockWorkManager: WorkManager
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var currentLiveData: MutableLiveData<CurrentUser?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockActivity = mockk<AppCompatActivity>(relaxed = true)
        mockServerRepository = mockk<ServerRepository>(relaxed = true)
        mockCache = mockk<SuggestionsCache>(relaxed = true)
        mockWorkManager = mockk<WorkManager>(relaxed = true)

        lifecycleRegistry = LifecycleRegistry(mockActivity)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        every { mockActivity.lifecycle } returns lifecycleRegistry
        val mockLifecycleScope = mockk<LifecycleCoroutineScope>(relaxed = true)
        every { mockLifecycleScope.coroutineContext } returns testScope.coroutineContext
        every { mockActivity.lifecycleScope } returns mockLifecycleScope

        currentLiveData = MutableLiveData(null)
        every { mockServerRepository.current } returns currentLiveData

        every { mockWorkManager.cancelUniqueWork(any()) } returns mockk<Operation>(relaxed = true)
        every { mockWorkManager.enqueue(any<androidx.work.WorkRequest>()) } returns mockk<Operation>(relaxed = true)
        every { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns mockk<Operation>(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockUser(
        userId: UUID = UUID.randomUUID(),
        serverId: UUID = UUID.randomUUID(),
    ): CurrentUser {
        val user =
            JellyfinUser(
                id = userId,
                name = "TestUser",
                serverId = serverId,
                accessToken = "token",
            )
        val server =
            JellyfinServer(
                id = serverId,
                name = "TestServer",
                url = "http://localhost",
                version = null,
            )
        return CurrentUser(user = user, server = server)
    }

    @Test
    fun constructor_throwsIllegalStateException_whenContextIsNotAppCompatActivity() {
        val nonActivityContext = mockk<Context>(relaxed = true)

        val exception =
            assertThrows(IllegalStateException::class.java) {
                SuggestionsSchedulerService(
                    context = nonActivityContext,
                    serverRepository = mockServerRepository,
                    cache = mockCache,
                    workManager = mockWorkManager,
                )
            }

        assert(exception.message!!.contains("SuggestionsSchedulerService requires an AppCompatActivity context"))
    }

    @Test
    fun init_cancelsWork_whenUserChangesToNull() =
        runTest {
            val observerSlot = slot<Observer<CurrentUser?>>()

            every {
                currentLiveData.observe(any<LifecycleOwner>(), capture(observerSlot))
            } answers {
                // Simulate LiveData behavior by calling observer with current value
                observerSlot.captured.onChanged(null)
            }

            SuggestionsSchedulerService(
                context = mockActivity,
                serverRepository = mockServerRepository,
                cache = mockCache,
                workManager = mockWorkManager,
            )

            verify { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
        }

    @Test
    fun init_schedulesPeriodicWork_whenUserIsSet() =
        runTest {
            val userWithServer = mockUser()
            val observerSlot = slot<Observer<CurrentUser?>>()

            coEvery { mockCache.isEmpty() } returns false
            every {
                currentLiveData.observe(any<LifecycleOwner>(), capture(observerSlot))
            } answers {
                observerSlot.captured.onChanged(userWithServer)
            }

            SuggestionsSchedulerService(
                context = mockActivity,
                serverRepository = mockServerRepository,
                cache = mockCache,
                workManager = mockWorkManager,
            )

            advanceUntilIdle()

            verify { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun init_schedulesImmediateFetch_whenCacheIsEmpty() =
        runTest {
            val userWithServer = mockUser()
            val observerSlot = slot<Observer<CurrentUser?>>()

            coEvery { mockCache.isEmpty() } returns true
            every {
                currentLiveData.observe(any<LifecycleOwner>(), capture(observerSlot))
            } answers {
                observerSlot.captured.onChanged(userWithServer)
            }

            SuggestionsSchedulerService(
                context = mockActivity,
                serverRepository = mockServerRepository,
                cache = mockCache,
                workManager = mockWorkManager,
            )

            advanceUntilIdle()

            // Should enqueue immediate one-time work when cache is empty
            verify { mockWorkManager.enqueue(any<androidx.work.WorkRequest>()) }
            // Should also schedule periodic work
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun init_doesNotScheduleImmediateFetch_whenCacheIsNotEmpty() =
        runTest {
            val userWithServer = mockUser()
            val observerSlot = slot<Observer<CurrentUser?>>()

            coEvery { mockCache.isEmpty() } returns false
            every {
                currentLiveData.observe(any<LifecycleOwner>(), capture(observerSlot))
            } answers {
                observerSlot.captured.onChanged(userWithServer)
            }

            SuggestionsSchedulerService(
                context = mockActivity,
                serverRepository = mockServerRepository,
                cache = mockCache,
                workManager = mockWorkManager,
            )

            advanceUntilIdle()

            // Should NOT enqueue immediate one-time work when cache is not empty
            verify(exactly = 0) { mockWorkManager.enqueue(any<androidx.work.WorkRequest>()) }
            // Should still schedule periodic work
            verify {
                mockWorkManager.enqueueUniquePeriodicWork(
                    SuggestionsWorker.WORK_NAME,
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun init_cancelsWorkBeforeScheduling_onUserChange() =
        runTest {
            val userWithServer = mockUser()
            val observerSlot = slot<Observer<CurrentUser?>>()

            coEvery { mockCache.isEmpty() } returns false
            every {
                currentLiveData.observe(any<LifecycleOwner>(), capture(observerSlot))
            } answers {
                // First emit null, then emit user
                observerSlot.captured.onChanged(null)
            }

            SuggestionsSchedulerService(
                context = mockActivity,
                serverRepository = mockServerRepository,
                cache = mockCache,
                workManager = mockWorkManager,
            )

            // Verify cancel was called for the first null emission
            verify(atLeast = 1) { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }

            // Now simulate user login
            observerSlot.captured.onChanged(userWithServer)
            advanceUntilIdle()

            // Cancel should be called again before scheduling
            verify(atLeast = 2) { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
        }
}
