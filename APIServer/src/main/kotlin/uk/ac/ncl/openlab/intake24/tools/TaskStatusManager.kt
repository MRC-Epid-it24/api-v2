package uk.ac.ncl.openlab.intake24.tools

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import uk.ac.ncl.openlab.intake24.dbutils.DatabaseClient
import uk.ac.ncl.openlab.intake24.systemsql.Tables
import java.time.OffsetDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes(
        JsonSubTypes.Type(value = CompletionStatus.Pending::class, name = "pending"),
        JsonSubTypes.Type(value = CompletionStatus.InProgress::class, name = "in_progress"),
        JsonSubTypes.Type(value = CompletionStatus.Finished::class, name = "finished"),
        JsonSubTypes.Type(value = CompletionStatus.Failed::class, name = "failed")
)
sealed class CompletionStatus {
    object Pending : CompletionStatus()
    class InProgress(@JsonProperty("progress") val progress: Float?) : CompletionStatus()
    class Finished(@JsonProperty("download") val download: Download?) : CompletionStatus()
    class Failed(@JsonProperty("reason") val reason: String) : CompletionStatus()
}

data class TaskInfo(val id: Int, val createdAt: OffsetDateTime, val status: CompletionStatus)

data class Download(val url: String, val expiresAt: OffsetDateTime)

@Singleton
class TaskStatusManager @Inject() constructor(@Named("system") val systemDatabase: DatabaseClient) {

    fun createTask(ownerId: Int, type: String): Int {
        return systemDatabase.runTransaction {
            it.insertInto(Tables.TOOLS_TASKS)
                    .columns(Tables.TOOLS_TASKS.USER_ID, Tables.TOOLS_TASKS.TYPE, Tables.TOOLS_TASKS.CREATED_AT)
                    .values(ownerId, type, OffsetDateTime.now())
                    .returning(Tables.TOOLS_TASKS.ID)
                    .fetchOne()[Tables.TOOLS_TASKS.ID]
        }
    }

    fun setStarted(taskId: Int) {
        systemDatabase.runTransaction {
            it.update(Tables.TOOLS_TASKS)
                    .set(Tables.TOOLS_TASKS.STARTED_AT, OffsetDateTime.now())
                    .where(Tables.TOOLS_TASKS.ID.eq(taskId))
                    .execute()
        }
    }

    fun updateProgress(taskId: Int, progress: Float) {
        systemDatabase.runTransaction {
            it.update(Tables.TOOLS_TASKS)
                    .set(Tables.TOOLS_TASKS.PROGRESS, progress)
                    .where(Tables.TOOLS_TASKS.ID.eq(taskId))
                    .execute()
        }
    }

    fun setSuccessful(taskId: Int, download: Download?) {
        systemDatabase.runTransaction {
            it.update(Tables.TOOLS_TASKS)
                    .set(Tables.TOOLS_TASKS.SUCCESSFUL, true)
                    .set(Tables.TOOLS_TASKS.PROGRESS, 1.0f)
                    .set(Tables.TOOLS_TASKS.COMPLETED_AT, OffsetDateTime.now())
                    .set(Tables.TOOLS_TASKS.DOWNLOAD_URL, download?.url)
                    .set(Tables.TOOLS_TASKS.DOWNLOAD_URL_EXPIRES_AT, download?.expiresAt)
                    .where(Tables.TOOLS_TASKS.ID.eq(taskId))
                    .execute()
        }
    }

    private fun collectStackTrace(throwable: Throwable): String {
        var current: Throwable? = throwable
        val result = StringBuilder()

        while (current != null) {
            val exceptionDesc = "${throwable.javaClass.name}: ${throwable.message}"

            if (result.isEmpty())
                result.append("Exception $exceptionDesc")
            else
                result.append("Caused by $exceptionDesc")

            throwable.stackTrace.forEach {
                result.append("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
            }

            current = throwable.cause
        }

        return result.toString()
    }


    fun setFailed(taskId: Int, cause: Throwable) {
        systemDatabase.runTransaction {
            it.update(Tables.TOOLS_TASKS)
                    .set(Tables.TOOLS_TASKS.COMPLETED_AT, OffsetDateTime.now())
                    .set(Tables.TOOLS_TASKS.SUCCESSFUL, false)
                    .set(Tables.TOOLS_TASKS.STACK_TRACE, collectStackTrace(cause))
                    .execute()
        }
    }

    fun getTaskList(ownerId: Int, type: String, since: OffsetDateTime): List<TaskInfo> {
        val rows = systemDatabase.runTransaction {
            it.select(Tables.TOOLS_TASKS.ID, Tables.TOOLS_TASKS.CREATED_AT, Tables.TOOLS_TASKS.STARTED_AT,
                    Tables.TOOLS_TASKS.COMPLETED_AT, Tables.TOOLS_TASKS.DOWNLOAD_URL, Tables.TOOLS_TASKS.DOWNLOAD_URL_EXPIRES_AT,
                    Tables.TOOLS_TASKS.PROGRESS, Tables.TOOLS_TASKS.SUCCESSFUL, Tables.TOOLS_TASKS.STACK_TRACE)
                    .from(Tables.TOOLS_TASKS)
                    .where(Tables.TOOLS_TASKS.USER_ID.eq(ownerId)
                            .and(Tables.TOOLS_TASKS.TYPE.eq(type))
                            .and(Tables.TOOLS_TASKS.CREATED_AT.ge(since)))
                    .orderBy(Tables.TOOLS_TASKS.CREATED_AT.desc())
                    .fetchArray()
        }

        return rows.map {

            val completionStatus = if (it[Tables.TOOLS_TASKS.COMPLETED_AT] != null && it[Tables.TOOLS_TASKS.SUCCESSFUL] != null) {
                if (it[Tables.TOOLS_TASKS.SUCCESSFUL]) {
                    val downloadUrl = it[Tables.TOOLS_TASKS.DOWNLOAD_URL]
                    val downloadUrlExpiresAt = it[Tables.TOOLS_TASKS.DOWNLOAD_URL_EXPIRES_AT]

                    val download = if (downloadUrl != null && downloadUrlExpiresAt != null)
                        Download(downloadUrl, downloadUrlExpiresAt)
                    else
                        null

                    CompletionStatus.Finished(download)
                } else {
                    CompletionStatus.Failed(it[Tables.TOOLS_TASKS.STACK_TRACE] ?: "")
                }
            } else {
                if (it[Tables.TOOLS_TASKS.STARTED_AT] != null) {
                    CompletionStatus.InProgress(it[Tables.TOOLS_TASKS.PROGRESS])
                } else {
                    CompletionStatus.Pending
                }
            }

            TaskInfo(it[Tables.TOOLS_TASKS.ID], it[Tables.TOOLS_TASKS.CREATED_AT], completionStatus)
        }
    }
}
