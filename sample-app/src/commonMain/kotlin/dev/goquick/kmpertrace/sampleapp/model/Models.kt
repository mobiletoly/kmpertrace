package dev.goquick.kmpertrace.sampleapp.model

data class Profile(
    val id: String,
    val name: String,
    val title: String,
    val email: String,
    val phone: String,
    val city: String
)

data class Contact(
    val id: String,
    val name: String,
    val relationship: String,
    val email: String
)

data class ActivityEvent(
    val id: String,
    val label: String,
    val timestamp: String,
    val description: String
)

data class DownloadState(
    val id: String,
    val label: String,
    val progressPercent: Int,
    val status: String
)

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

data class ProfileScreenState(
    val profile: LoadState<Profile> = LoadState.Loading,
    val contacts: LoadState<List<Contact>> = LoadState.Loading,
    val activity: LoadState<List<ActivityEvent>> = LoadState.Loading,
    val lastRefreshed: String? = null,
    val downloads: List<DownloadState> = emptyList()
)
