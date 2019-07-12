package uk.ac.ncl.openlab.intake24.http4k

import com.google.inject.Inject
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import uk.ac.ncl.intake24.serialization.StringCodec
import uk.ac.ncl.openlab.intake24.services.CopyFoodV2
import uk.ac.ncl.openlab.intake24.services.CopyLocalV2
import uk.ac.ncl.openlab.intake24.services.FoodsServiceV2


data class CopyFoodsV2Request(val foods: List<CopyFoodV2>)

data class CopyLocalFoodsV2Request(val sourceLocale: String, val destLocale: String, val foods: List<CopyLocalV2>)

class FoodsController @Inject constructor(private val service: FoodsServiceV2,
                                          private val stringCodec: StringCodec) {

    fun copyFoods(user: Intake24User, request: Request): Response {
        val request = stringCodec.decode(request.bodyString(), CopyFoodsV2Request::class)
        service.copyFoods(request.foods)
        return Response(Status.OK)
    }

    fun copyLocalFoods(user: Intake24User, request: Request): Response {
        val request = stringCodec.decode(request.bodyString(), CopyLocalFoodsV2Request::class)
        service.copyLocalFoods(request.sourceLocale, request.destLocale, request.foods)
        return Response(Status.OK)

    }
}