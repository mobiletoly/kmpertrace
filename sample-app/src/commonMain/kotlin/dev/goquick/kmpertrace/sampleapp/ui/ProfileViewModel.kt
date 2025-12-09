package dev.goquick.kmpertrace.sampleapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.KmperTrace
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.sampleapp.data.FakeDownloader
import dev.goquick.kmpertrace.sampleapp.data.ProfileRepository
import dev.goquick.kmpertrace.sampleapp.model.DownloadState
import dev.goquick.kmpertrace.sampleapp.model.LoadState
import dev.goquick.kmpertrace.sampleapp.model.ProfileScreenState
import dev.goquick.kmpertrace.trace.traceSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

class ProfileViewModel(
    private val repo: ProfileRepository,
    private val downloader: FakeDownloader,
    private val scope: CoroutineScope
) {
    var state by mutableStateOf(ProfileScreenState())
        private set
    private val log = Log.forClass<ProfileViewModel>()

    fun refreshAll(userId: String = DEFAULT_USER_ID) {
        log.d { "refreshAll called for $userId" }
        log.i { "starting now" }
        scope.launch {
            traceSpan(component = "ProfileViewModel", operation = "refreshAll", attributes = mapOf("userId" to userId)) {
                state = state.copy(
                    profile = LoadState.Loading,
                    contacts = LoadState.Loading,
                    activity = LoadState.Loading
                )
                try {
                    log.withOperation("refreshAll").d { "Starting refresh for $userId" }
                    val result = loadAll(userId)
                    state = state.copy(
                        profile = LoadState.Success(result.profile),
                        contacts = LoadState.Success(result.contacts),
                        activity = LoadState.Success(result.activity),
                        lastRefreshed = "just now"
                    )
                    log.withOperation("refreshAll").d { "Profile=${result.profile.name}, contacts=${result.contacts.size}, activity=${result.activity.size}" }
                    log.withOperation("refreshAll").i { "Refresh complete for $userId" }
                } catch (t: Throwable) {
                    log.withOperation("refreshAll").e(throwable = t) { "Refresh failed: ${t.message}" }
                    state = state.copy(
                        profile = LoadState.Error(t.message ?: "Unknown error"),
                        contacts = LoadState.Error(t.message ?: "Unknown error"),
                        activity = LoadState.Error(t.message ?: "Unknown error")
                    )
                }
            }
            log.d { "refreshAll finished for $userId" }
        }
    }

    fun refreshActivityOnly(userId: String = DEFAULT_USER_ID) {
        scope.launch {
            traceSpan(component = "ProfileViewModel", operation = "refreshActivity", attributes = mapOf("userId" to userId)) {
                state = state.copy(activity = LoadState.Loading)
                runCatching { repo.loadActivity(userId) }
                    .onSuccess { events ->
                        state = state.copy(activity = LoadState.Success(events))
                        log.withOperation("refreshActivity").d { "Activity refreshed (${events.size} events)" }
                    }
                    .onFailure { t ->
                        log.withOperation("refreshActivity").w(throwable = t) { "Activity refresh failed" }
                        state = state.copy(activity = LoadState.Error(t.message ?: "Error"))
                    }
            }
        }
    }

    private suspend fun loadAll(userId: String) = coroutineScope {
        val profile = async { repo.loadProfile(userId) }
        val contacts = async { repo.loadContacts(userId) }
        val activity = async { repo.loadActivity(userId) }
        Loaded(
            profile = profile.await(),
            contacts = contacts.await(),
            activity = activity.await()
        )
    }

    fun startDownload(label: String) {
        val jobId = "${label}-${Random.nextLong()}"
        log.d { "startDownload called for $label" }
        state = state.copy(
            downloads = state.downloads + DownloadState(
                id = jobId,
                label = label,
                progressPercent = 0,
                status = "In progress"
            )
        )
        scope.launch {
            traceSpan(component = "ProfileViewModel", operation = "download-$label", attributes = mapOf("jobId" to jobId, "label" to label)) {
                runCatching {
                    downloader.download(label = label, totalChunks = 3, chunkDelayMs = 500) { percent ->
                        state = state.copy(
                            downloads = state.downloads.map {
                                if (it.id == jobId) it.copy(progressPercent = percent) else it
                            }
                        )
                    }
                }.onSuccess {
                    state = state.copy(
                        downloads = state.downloads.map {
                            if (it.id == jobId) it.copy(progressPercent = 100, status = "Complete") else it
                        }
                    )
                }.onFailure { t ->
                    state = state.copy(
                        downloads = state.downloads.map {
                            if (it.id == jobId) it.copy(status = "Failed: ${t.message ?: "error"}") else it
                        }
                    )
                }
            }
        }
    }

    fun triggerFailure(userId: String = DEFAULT_USER_ID) {
        scope.launch {
            try {
                traceSpan(component = "ProfileViewModel", operation = "simulateFailure", attributes = mapOf("userId" to userId)) {
                    log.withOperation("simulateFailure").w { "Simulated failure about to throw for $userId" }
                    throw IllegalStateException("Simulated failure for $userId")
                }
            } catch (t: Throwable) {
                log.withOperation("simulateFailure").e(throwable = t) { "Simulated failure triggered" }
                state = state.copy(
                    profile = LoadState.Error("Simulated failure"),
                    contacts = LoadState.Error("Simulated failure"),
                    activity = LoadState.Error("Simulated failure")
                )
            }
        }
    }

    private data class Loaded(
        val profile: dev.goquick.kmpertrace.sampleapp.model.Profile,
        val contacts: List<dev.goquick.kmpertrace.sampleapp.model.Contact>,
        val activity: List<dev.goquick.kmpertrace.sampleapp.model.ActivityEvent>
    )

    companion object {
        const val DEFAULT_USER_ID = "user-123"
    }
}
