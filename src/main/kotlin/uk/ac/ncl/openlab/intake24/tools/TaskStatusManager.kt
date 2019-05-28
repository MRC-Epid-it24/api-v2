package uk.ac.ncl.openlab.intake24.tools

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes(
        JsonSubTypes.Type(value = CompletionStatus.Pending::class, name = "pending"),
        JsonSubTypes.Type(value = CompletionStatus.InProgress::class, name = "in_progress"),
        JsonSubTypes.Type(value = CompletionStatus.Finished::class, name = "finished"),
        JsonSubTypes.Type(value = CompletionStatus.Failed::class, name = "failed")
)
sealed class CompletionStatus {
    object Pending : CompletionStatus()
    class InProgress(val progress: Double) : CompletionStatus()
    class Finished(val file: Path?) : CompletionStatus()
    class Failed(val cause: Throwable) : CompletionStatus()
}

class TaskStatusManager {

    private val counter = AtomicInteger(0)
    private val tasks = ConcurrentHashMap<Int, CompletionStatus>()

    fun registerNewTask(): Int {
        val id = counter.getAndIncrement()
        tasks[id] = CompletionStatus.Pending
        return id
    }

    fun updateTask(id: Int, status: CompletionStatus) {
        tasks[id] = status
    }

    fun getTaskStatus(id: Int): CompletionStatus? {
        return tasks[id];
    }
}