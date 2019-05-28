package uk.ac.ncl.openlab.intake24.tools

import java.util.concurrent.ConcurrentHashMap

sealed class CompletionStatus {
    object Pending : CompletionStatus()
    class InProgress(val progress: Double) : CompletionStatus()
    object Finished : CompletionStatus()
    class Failed(val cause: Throwable) : CompletionStatus()
}

data class TaskStatus(val completion: CompletionStatus, val )

class TaskStatusManager {

    val tasks = ConcurrentHashMap<Int, TaskStatus>()

    fun registerNewTask()


}