package com.rockthejvm.reviewboard.http.controllers

import zio.*
import sttp.tapir.server.ServerEndpoint

import com.rockthejvm.reviewboard.http.endpoints.ReviewEndpoints
import com.rockthejvm.reviewboard.services.ReviewService
import com.rockthejvm.reviewboard.services.JWTService
import com.rockthejvm.reviewboard.domain.data.UserID

class ReviewController private (reviewService: ReviewService, jwtService: JWTService)
    extends BaseController
    with ReviewEndpoints {

  val create: ServerEndpoint[Any, Task] =
    createEndpoint
      .serverSecurityLogic[UserID, Task](token => jwtService.verifyToken(token).either)
      .serverLogic(userId => req => reviewService.create(req, userId.id).either)

  val getById: ServerEndpoint[Any, Task] =
    getByIdEndpoint.serverLogic(id => reviewService.getById(id).either)

  val getByCompanyId: ServerEndpoint[Any, Task] =
    getByCompanyIdEndpoint.serverLogic(companyId => reviewService.getByCompanyId(companyId).either)

  val getSummary: ServerEndpoint[Any, Task] =
    getSummaryEndpoint.serverLogic(companyId => reviewService.getSummary(companyId).either)

  val makeSummary: ServerEndpoint[Any, Task] =
    makeSummaryEndpoint.serverLogic(companyId => reviewService.makeSummary(companyId).either)

  override val routes: List[ServerEndpoint[Any, Task]] =
    List(getSummary, makeSummary, create, getById, getByCompanyId)
}

object ReviewController {
  val makeZIO =
    for {
      reviewService <- ZIO.service[ReviewService]
      jwtService    <- ZIO.service[JWTService]
    } yield new ReviewController(reviewService, jwtService)
}
