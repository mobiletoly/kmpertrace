package dev.goquick.kmpertrace.sampleapp.data

import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.sampleapp.model.ActivityEvent
import dev.goquick.kmpertrace.sampleapp.model.Contact
import dev.goquick.kmpertrace.sampleapp.model.Profile
import dev.goquick.kmpertrace.trace.traceSpan

class ProfileRepository(
    private val network: FakeNetworkService,
    private val database: FakeDatabase
) {

    private val log = Log.forComponent("ProfileRepository")

    suspend fun loadProfile(userId: String): Profile = traceSpan(component = "ProfileRepository", operation = "loadProfile", attributes = mapOf("userId" to userId)) {
        log.withOperation("loadProfile").d { "loadProfile begin for $userId" }
        database.loadProfile(userId)?.let {
            log.withOperation("loadProfile").d { "Profile cache hit" }
            return@traceSpan it
        }
        val fresh = network.fetchProfile(userId)
        database.saveProfile(fresh)
        log.withOperation("loadProfile").i { "Profile fetched from network" }
        log.withOperation("loadProfile").d { "loadProfile finished for $userId" }
        fresh
    }

    suspend fun loadContacts(userId: String): List<Contact> = traceSpan(component = "ProfileRepository", operation = "loadContacts", attributes = mapOf("userId" to userId)) {
        log.withOperation("loadContacts").d { "loadContacts begin for $userId" }
        database.loadContacts(userId)?.let {
            log.withOperation("loadContacts").d { "Contacts cache hit" }
            return@traceSpan it
        }
        val fresh = network.fetchContacts(userId)
        database.saveContacts(userId, fresh)
        log.withOperation("loadContacts").d { "loadContacts finished for $userId" }
        fresh
    }

    suspend fun loadActivity(userId: String): List<ActivityEvent> = traceSpan(component = "ProfileRepository", operation = "loadActivity", attributes = mapOf("userId" to userId)) {
        log.withOperation("loadActivity").d { "loadActivity begin for $userId" }
        database.loadActivity(userId)?.let {
            log.withOperation("loadActivity").d { "Activity cache hit" }
            return@traceSpan it
        }
        val events = network.fetchActivity(userId)
        database.saveActivity(userId, events)
        log.withOperation("loadActivity").d { "loadActivity finished for $userId" }
        events
    }
}
