package com.rockthejvm.reviewboard.services

import zio.*
import zio.test.*

import com.rockthejvm.reviewboard.config.JWTConfig
import com.rockthejvm.reviewboard.domain.data.User

object JWTServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JWTServiceSpec")(
      test("create and validate token") {
        for {
          service <- ZIO.service[JWTService]
          userToken <- service.createToken(
            User(1L, "daniel@rockthejvm.com", "unimportant")
          ) // string
          userId <- service.verifyToken(userToken.token)
        } yield assertTrue(
          userId.id == 1L &&
            userId.email == "daniel@rockthejvm.com"
        )
      }
    ).provide(
      JWTServiceLive.layer,
      ZLayer.succeed(JWTConfig("secret", 3600))
    )
}
