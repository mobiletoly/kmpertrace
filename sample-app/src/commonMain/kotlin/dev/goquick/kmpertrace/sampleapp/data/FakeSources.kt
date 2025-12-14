package dev.goquick.kmpertrace.sampleapp.data

import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.sampleapp.model.ActivityEvent
import dev.goquick.kmpertrace.sampleapp.model.Contact
import dev.goquick.kmpertrace.sampleapp.model.Profile
import dev.goquick.kmpertrace.trace.traceSpan
import kotlinx.coroutines.delay

class FakeNetworkService {
    private val log = Log.forComponent("NetworkService")

    suspend fun fetchProfile(userId: String): Profile =
        traceSpan(component = "NetworkService", operation = "fetchProfile", attributes = mapOf("userId" to userId)) {
        log.withOperation("fetchProfile").d { "Starting profile fetch for $userId" }
        delay(500)
        log.withOperation("fetchProfile").d { "Fetched profile for $userId from network" }
        Profile(
            id = userId,
            name = "Alex Parker",
            title = "Staff Engineer",
            email = "alex.parker@example.com",
            phone = "+1 415-555-1234",
            city = "San Francisco"
        )
    }

    suspend fun fetchContacts(userId: String): List<Contact> =
        traceSpan(component = "NetworkService", operation = "fetchContacts", attributes = mapOf("userId" to userId)) {
        log.withOperation("fetchContacts").d { "Requesting contacts for $userId" }
        delay(280)
        log.withOperation("fetchContacts").d { "Fetched contacts for $userId from network" }
        listOf(
            Contact("c1", "Jamie Nguyen", "Manager", "jamie.nguyen@example.com"),
            Contact("c2", "Priya Patel", "Teammate", "priya.patel@example.com"),
            Contact("c3", "Morgan Lee", "Product Partner", "morgan.lee@example.com")
        )
    }

    suspend fun fetchActivity(userId: String): List<ActivityEvent> =
        traceSpan(component = "NetworkService", operation = "fetchActivity", attributes = mapOf("userId" to userId)) {
        log.withOperation("fetchActivity").d { "Requesting activity timeline for $userId" }
        delay(220)
        log.withOperation("fetchActivity").d { "Fetched activity timeline for $userId" }
        listOf(
            ActivityEvent("a1", "System Sync", "2025-01-02T09:15Z", "Refreshed user roles and permissions."),
            ActivityEvent("a2", "Profile Update", "2025-01-01T21:03Z", "Updated contact preferences."),
            ActivityEvent("a3", "Login", "2025-01-01T08:47Z", "Successful login from web.")
        )
    }
}

class FakeDatabase {
    private val log = Log.forComponent("Database")
    private var cachedProfile: Profile? = null
    private var cachedContacts: List<Contact>? = null
    private var cachedActivity: List<ActivityEvent>? = null

    suspend fun loadProfile(userId: String): Profile? =
        traceSpan(component = "Database", operation = "loadProfile", attributes = mapOf("userId" to userId)) {
        delay(40)
        log.withOperation("loadProfile").d { "Checking profile cache for $userId" }
        log.withOperation("loadProfile").d { "Loaded profile cache for $userId = ${cachedProfile != null}" }
        cachedProfile
    }

    suspend fun saveProfile(profile: Profile) =
        traceSpan(component = "Database", operation = "saveProfile", attributes = mapOf("userId" to profile.id)) {
        delay(30)
        log.withOperation("saveProfile").d { "Saved profile cache for ${profile.id}" }
        cachedProfile = profile
    }

    suspend fun loadContacts(userId: String): List<Contact>? =
        traceSpan(component = "Database", operation = "loadContacts", attributes = mapOf("userId" to userId)) {
        delay(30)
        log.withOperation("loadContacts").d { "Checking contacts cache for $userId" }
        log.withOperation("loadContacts").d { "Loaded contacts cache for $userId = ${cachedContacts?.size ?: 0}" }
        cachedContacts
    }

    suspend fun saveContacts(userId: String, contacts: List<Contact>) =
        traceSpan(component = "Database", operation = "saveContacts", attributes = mapOf("userId" to userId)) {
        delay(25)
        log.withOperation("saveContacts").d { "Saved ${contacts.size} contacts for $userId" }
        cachedContacts = contacts
    }

    suspend fun loadActivity(userId: String): List<ActivityEvent>? =
        traceSpan(component = "Database", operation = "loadActivity", attributes = mapOf("userId" to userId)) {
        delay(20)
        log.withOperation("loadActivity").d { "Checking activity cache for $userId" }
        log.withOperation("loadActivity").d { "Loaded activity cache for $userId = ${cachedActivity?.size ?: 0}" }
        cachedActivity
    }

    suspend fun saveActivity(userId: String, events: List<ActivityEvent>) =
        traceSpan(component = "Database", operation = "saveActivity", attributes = mapOf("userId" to userId)) {
        delay(20)
        log.withOperation("saveActivity").d { "Saved ${events.size} activity events for $userId" }
        cachedActivity = events
    }
}
