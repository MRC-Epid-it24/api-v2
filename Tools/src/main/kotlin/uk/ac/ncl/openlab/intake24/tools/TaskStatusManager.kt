package uk.ac.ncl.openlab.intake24.tools

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.inject.Inject
import com.google.inject.Singleton
import uk.ac.ncl.intake24.storage.SharedStorageWithSerializer
import java.time.Duration
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes(
        JsonSubTypes.Type(value = CompletionStatus.Pending::class, name = "pending"),
        JsonSubTypes.Type(value = CompletionStatus.InProgress::class, name = "in_progress"),
        JsonSubTypes.Type(value = CompletionStatus.Finished::class, name = "finished"),
        JsonSubTypes.Type(value = CompletionStatus.Failed::class, name = "failed")
)
sealed class CompletionStatus {
    object Pending : CompletionStatus()
    class InProgress(@JsonProperty("progress") val progress: Double) : CompletionStatus()
    class Finished(@JsonProperty("url") val url: String) : CompletionStatus()
    class Failed(@JsonProperty("reason") val reason: String) : CompletionStatus()
}

@Singleton
class TaskStatusManager @Inject() constructor(val sharedStorage: SharedStorageWithSerializer) {

    fun registerNewTask(): String {
        val id = UUID.randomUUID().toString()
        sharedStorage.put(id, CompletionStatus.Pending, Duration.ofHours(2))
        return id
    }

    fun updateTask(id: String, status: CompletionStatus) {
        sharedStorage.put(id, status, Duration.ofHours(2))
    }

    fun removeTask(id: String) {
        sharedStorage.remove(id)
    }

    fun getTaskStatus(id: String): CompletionStatus? {
        return sharedStorage.get(id)
    }
}
