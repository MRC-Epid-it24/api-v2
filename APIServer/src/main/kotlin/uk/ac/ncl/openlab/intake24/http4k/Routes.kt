package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Method
import org.http4k.routing.bind
import org.http4k.routing.routes

class Routes @Inject() constructor(foodRoutes: FoodRoutes,
                                   surveyRoutes: SurveyRoutes,
                                   taskStatusController: TaskStatusController,
                                   fileDownloadController: LocalSecureURLController,
                                   security: Security) {
    val router = routes(
            "/tasks/{id}" bind Method.GET to security.allowAnyAuthenticated(taskStatusController::getTaskStatus),
            "/tasks" bind Method.GET to security.allowAnyAuthenticated(taskStatusController::getTasksList),
            "/files/download" bind Method.GET to fileDownloadController::download,

            foodRoutes.router.withBasePath("/foods"),
            surveyRoutes.router.withBasePath("/surveys"))
}
