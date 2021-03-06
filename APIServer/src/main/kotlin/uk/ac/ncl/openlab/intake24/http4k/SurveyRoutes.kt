package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Method
import org.http4k.routing.bind
import org.http4k.routing.routes

class SurveyRoutes @Inject() constructor(controller: NutrientMappingController,
                                         surveysController: SurveysController,
                                         security: Security) {
    val router = routes(
            "/{surveyId}/recalculate-nutrients" bind Method.POST to security.allowSurveyAdmins(controller::recalculateNutrients),
            "/{surveyId}/submission-count" bind Method.GET to security.allowSurveyAdminsOrStaff(surveysController::getSubmissionsCount))
}
