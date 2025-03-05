package com.rockthejvm.reviewboard

import sttp.tapir.*
import sttp.tapir.server.ziohttp.*
import zio.*
import zio.http.Server

import com.rockthejvm.reviewboard.http.controllers.*
import com.rockthejvm.reviewboard.http.HttpApi
import com.rockthejvm.reviewboard.services.*
import com.rockthejvm.reviewboard.repositories.*
import com.rockthejvm.reviewboard.config.*
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import zio.http.ServerConfig
import java.net.InetSocketAddress

object Application extends ZIOAppDefault {

  val configuredServer =
    Configs.makeLayer[HttpConfig]("rockthejvm.http") >>>
      ZLayer(
        ZIO
          .service[HttpConfig]
          .map(config => ServerConfig.default.copy(address = InetSocketAddress(config.port)))
      ) >>> Server.live

  def runMigrations = for {
    flyway <- ZIO.service[FlywayService]
    _ <- flyway.runMigrations.catchSome { case e =>
      ZIO.logError("MIGRATIONS FAILED: " + e) *>
        flyway.runRepairs *> flyway.runMigrations
    }
  } yield ()

  def startServer = for {
    endpoints <- HttpApi.endpointsZIO
    _ <- Server.serve(
      ZioHttpInterpreter(
        ZioHttpServerOptions.default.appendInterceptor(
          CORSInterceptor.default
        )
      ).toHttp(endpoints)
    )
    _ <- Console.printLine("Rock the JVM!")
  } yield ()

  def program = for {
    _ <- ZIO.log("Rock the JVM! Bootstrapping...")
    _ <- runMigrations
    _ <- startServer
  } yield ()

  override def run =
    program.provide(
      configuredServer,
      // services
      CompanyServiceLive.layer,
      ReviewServiceLive.configuredLayer,
      UserServiceLive.layer,
      JWTServiceLive.configuredLayer,
      EmailServiceLive.configuredLayer,
      InviteServiceLive.configuredLayer,
      PaymentServiceLive.configuredLayer,
      OpenAIServiceLive.configuredLayer,
      FlywayServiceLive.configuredLayer,
      // repos
      CompanyRepositoryLive.layer,
      ReviewRepositoryLive.layer,
      UserRepositoryLive.layer,
      RecoveryTokensRepositoryLive.configuredLayer,
      InviteRepositoryLive.layer,
      // other requirements
      Repository.dataLayer
    )
}
