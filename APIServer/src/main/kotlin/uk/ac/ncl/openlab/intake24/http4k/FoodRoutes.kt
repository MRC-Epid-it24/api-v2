package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.routing.routes

class FoodRoutes @Inject() constructor(fctRoutes: FoodCompositionRoutes,
                                       foodAdminRoutes: FoodAdminRoutes) {
    val router =
            routes(foodAdminRoutes.router.withBasePath("/admin"),
                    fctRoutes.router.withBasePath("/composition"))
}