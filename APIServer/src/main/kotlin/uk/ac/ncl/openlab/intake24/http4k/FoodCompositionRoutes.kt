package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes

class FoodCompositionRoutes @Inject() constructor(fctController: FoodCompositionTableController,
                                                  security: Security) {

    private fun canWriteTables(user: Intake24User, request: Request): Boolean {
        return user.hasRole(Intake24Roles.foodsAdmin)
    }

    private fun canReadTables(user: Intake24User, request: Request): Boolean {
        return user.hasRole(Intake24Roles.foodsAdmin)
    }

    val router = routes(
            "/tables" bind Method.GET to security.check(::canReadTables, fctController::getCompositionTables),
            "/tables" bind Method.POST to security.check(::canWriteTables, fctController::createCompositionTable),
            "/tables/{tableId}" bind Method.GET to security.check(::canReadTables, fctController::getCompositionTable),
            "/tables/{tableId}/csv" bind Method.PATCH to security.check(::canWriteTables, fctController::uploadCsv),
            "/tables/{tableId}" bind Method.PATCH to security.check(::canWriteTables, fctController::updateCompositionTable),

            "/nutrients" bind Method.GET to security.check(::canReadTables, fctController::getNutrientTypes)
    )
}