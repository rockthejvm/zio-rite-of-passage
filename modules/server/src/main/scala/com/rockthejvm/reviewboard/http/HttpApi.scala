package com.rockthejvm.reviewboard.http

import com.rockthejvm.reviewboard.http.controllers.*

object HttpApi {
  def gatherRoutes(controllers: List[BaseController]) =
    controllers.flatMap(_.routes)

  def makeControllers = for {
    health    <- HealthController.makeZIO
    companies <- CompanyController.makeZIO
    reviews   <- ReviewController.makeZIO
    users     <- UserController.makeZIO
    invites   <- InviteController.makeZIO
    // add new controllers here
  } yield List(health, companies, reviews, users, invites)

  val endpointsZIO = makeControllers.map(gatherRoutes)
}
