package com.rockthejvm.reviewboard.integration

import zio.*
import zio.test.*
import zio.json.*
import sttp.client3.*
import sttp.tapir.generic.auto.*

import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import sttp.tapir.ztapir.RIOMonadError

import com.rockthejvm.reviewboard.services.*
import com.rockthejvm.reviewboard.http.controllers.*
import com.rockthejvm.reviewboard.repositories.*
import com.rockthejvm.reviewboard.config.JWTConfig
import sttp.model.Method
import com.rockthejvm.reviewboard.config.RecoveryTokensConfig
import com.rockthejvm.reviewboard.domain.data.UserToken
import com.rockthejvm.reviewboard.http.requests.{
  DeleteAccountRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RecoverPasswordRequest,
  RegisterUserAccount,
  UpdatePasswordRequest
}
import com.rockthejvm.reviewboard.http.responses.UserResponse
import com.rockthejvm.reviewboard.domain.data.Company

object UserFlowSpec extends ZIOSpecDefault with RepositorySpec {
  // http controller
  // service
  // repository
  // test container

  override val initScript: String = "sql/integration.sql"

  private given zioME: MonadError[Task] = new RIOMonadError[Any]

  private def backendStubZIO =
    for {
      controller <- UserController.makeZIO
      backendStub <- ZIO.succeed(
        TapirStubInterpreter(SttpBackendStub(MonadError[Task]))
          .whenServerEndpointsRunLogic(controller.routes)
          .backend()
      )
    } yield backendStub

  extension [A: JsonCodec](backend: SttpBackend[Task, Nothing]) {
    def sendRequest[B: JsonCodec](
        method: Method,
        path: String,
        payload: A,
        maybeToken: Option[String] = None
    ): Task[Option[B]] =
      basicRequest
        .method(method, uri"$path")
        .body(payload.toJson)
        .auth
        .bearer(maybeToken.getOrElse(""))
        .send(backend)
        .map(_.body)
        .map(_.toOption.flatMap(payload => payload.fromJson[B].toOption))

    def post[B: JsonCodec](path: String, payload: A): Task[Option[B]] =
      sendRequest(Method.POST, path, payload, None)
    def postAuth[B: JsonCodec](path: String, payload: A, token: String): Task[Option[B]] =
      sendRequest(Method.POST, path, payload, Some(token))
    def postNoResponse(path: String, payload: A): Task[Unit] =
      basicRequest
        .method(Method.POST, uri"$path")
        .body(payload.toJson)
        .send(backend)
        .unit
    def put[B: JsonCodec](path: String, payload: A): Task[Option[B]] =
      sendRequest(Method.PUT, path, payload, None)
    def putAuth[B: JsonCodec](path: String, payload: A, token: String): Task[Option[B]] =
      sendRequest(Method.PUT, path, payload, Some(token))
    def delete[B: JsonCodec](path: String, payload: A): Task[Option[B]] =
      sendRequest(Method.DELETE, path, payload, None)
    def deleteAuth[B: JsonCodec](path: String, payload: A, token: String): Task[Option[B]] =
      sendRequest(Method.DELETE, path, payload, Some(token))
  }

  class EmailServiceProbe extends EmailService("example.com") {
    val db = collection.mutable.Map[String, String]()
    override def sendEmail(to: String, subject: String, content: String): Task[Unit] = ZIO.unit
    override def sendPasswordRecoveryEmail(to: String, token: String): Task[Unit] =
      ZIO.succeed(db += (to -> token))
    // specific to the test
    def probeToken(email: String): Task[Option[String]] = ZIO.succeed(db.get(email))
  }

  val emailServiceLayer: ZLayer[Any, Nothing, EmailServiceProbe] =
    ZLayer.succeed(new EmailServiceProbe)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("UserFlowSpec")(
      test("create user") {
        for {
          backendStub <- backendStubZIO
          maybeResponse <- backendStub
            .post[UserResponse](
              "/users",
              RegisterUserAccount("daniel@rockthejvm.com", "rockthejvm")
            )
        } yield assertTrue(maybeResponse.contains(UserResponse("daniel@rockthejvm.com")))
      },
      test("create and log in") {
        for {
          backendStub <- backendStubZIO
          maybeResponse <- backendStub.post[UserResponse](
            "/users",
            RegisterUserAccount("daniel@rockthejvm.com", "rockthejvm")
          )
          maybeToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "rockthejvm"))
        } yield assertTrue(
          maybeToken.filter(_.email == "daniel@rockthejvm.com").nonEmpty
        )
      },
      test("change password") {
        for {
          backendStub <- backendStubZIO
          maybeResponse <- backendStub.post[UserResponse](
            "/users",
            RegisterUserAccount("daniel@rockthejvm.com", "rockthejvm")
          )
          userToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "rockthejvm"))
            .someOrFail(new RuntimeException("Authentication failed"))
          _ <- backendStub
            .putAuth[UserResponse](
              "/users/password",
              UpdatePasswordRequest("daniel@rockthejvm.com", "rockthejvm", "scalarulez"),
              userToken.token
            )
          maybeOldToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "rockthejvm"))
          maybeNewToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "scalarulez"))
        } yield assertTrue(
          maybeOldToken.isEmpty && maybeNewToken.nonEmpty
        )
      },
      test("delete user") {
        for {
          backendStub <- backendStubZIO
          userRepo    <- ZIO.service[UserRepository]
          maybeResponse <- backendStub.post[UserResponse](
            "/users",
            RegisterUserAccount("daniel@rockthejvm.com", "rockthejvm")
          )
          maybeOldUser <- userRepo.getByEmail("daniel@rockthejvm.com")
          userToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "rockthejvm"))
            .someOrFail(new RuntimeException("Authentication failed"))
          _ <- backendStub
            .deleteAuth[UserResponse](
              "/users",
              DeleteAccountRequest("daniel@rockthejvm.com", "rockthejvm"),
              userToken.token
            )
          maybeUser <- userRepo.getByEmail("daniel@rockthejvm.com")
        } yield assertTrue(
          maybeOldUser.filter(_.email == "daniel@rockthejvm.com").nonEmpty && maybeUser.isEmpty
        )
      },
      test("recover password flow")(
        for {
          backendStub <- backendStubZIO
          // register a user
          _ <- backendStub.post[UserResponse](
            "/users",
            RegisterUserAccount("daniel@rockthejvm.com", "rockthejvm")
          )
          // trigger recover password flow
          _ <- backendStub.postNoResponse(
            "/users/forgot",
            ForgotPasswordRequest("daniel@rockthejvm.com")
          )
          // somehow fetch the token I was sent
          emailServiceProbe <- ZIO.service[EmailServiceProbe]
          token <- emailServiceProbe
            .probeToken("daniel@rockthejvm.com")
            .someOrFail(new RuntimeException("token was NOT emailed!"))
          _ <- backendStub.postNoResponse(
            "/users/recover",
            RecoverPasswordRequest("daniel@rockthejvm.com", token, "scalarulez")
          )
          maybeOldToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "rockthejvm"))
          maybeNewToken <- backendStub
            .post[UserToken]("/users/login", LoginRequest("daniel@rockthejvm.com", "scalarulez"))
        } yield assertTrue(
          maybeOldToken.isEmpty && maybeNewToken.nonEmpty
        )
      )
    ).provide(
      UserServiceLive.layer,
      JWTServiceLive.layer,
      UserRepositoryLive.layer,
      RecoveryTokensRepositoryLive.layer,
      emailServiceLayer,
      Repository.quillLayer,
      dataSourceLayer,
      ZLayer.succeed(JWTConfig("secret", 3600)),
      ZLayer.succeed(RecoveryTokensConfig(24 * 3600)),
      Scope.default
    )
}
