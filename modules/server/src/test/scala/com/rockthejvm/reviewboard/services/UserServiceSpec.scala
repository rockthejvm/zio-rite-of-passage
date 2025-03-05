package com.rockthejvm.reviewboard.services

import zio.*
import zio.test.*
import com.rockthejvm.reviewboard.repositories.UserRepository
import com.rockthejvm.reviewboard.domain.data.User
import com.rockthejvm.reviewboard.domain.data.UserToken
import com.rockthejvm.reviewboard.domain.data.UserID
import com.rockthejvm.reviewboard.repositories.RecoveryTokensRepository

object UserServiceSpec extends ZIOSpecDefault {

  val daniel = User(
    1L,
    "daniel@rockthejvm.com",
    "1000:E477E35CDF726E84AE17A7B04F884693EEEABB52D238A0B5:202742527DB184C2AA68B41C30C593D2A2C00BF8550C6E9F"
  )

  val stubRepoLayer = ZLayer.succeed {
    new UserRepository {
      val db = collection.mutable.Map[Long, User](1L -> daniel)

      override def create(user: User): Task[User] = ZIO.succeed {
        db += (user.id -> user)
        user
      }

      override def getById(id: Long): Task[Option[User]] =
        ZIO.succeed(db.get(id))

      override def getByEmail(email: String): Task[Option[User]] =
        ZIO.succeed(db.values.find(_.email == email))

      override def update(id: Long, op: User => User): Task[User] = ZIO.attempt {
        val newUser = op(db(id))
        db += (newUser.id -> newUser)
        newUser
      }

      override def delete(id: Long): Task[User] = ZIO.attempt {
        val user = db(id)
        db -= id
        user
      }
    }
  }

  val stubTokenRepoLayer = ZLayer.succeed {
    new RecoveryTokensRepository {
      val db = collection.mutable.Map[String, String]()
      override def checkToken(email: String, token: String): Task[Boolean] =
        ZIO.succeed(db.get(email).filter(_ == token).nonEmpty)
      override def getToken(email: String): Task[Option[String]] = ZIO.attempt {
        val token = util.Random.alphanumeric.take(8).mkString.toUpperCase()
        db += (email -> token)
        Some(token)
      }
    }
  }

  val stubEmailsLayer = ZLayer.succeed {
    new EmailService("example.com") {
      override def sendEmail(to: String, subject: String, content: String): Task[Unit] = ZIO.unit
    }
  }

  val stubJwtLayer = ZLayer.succeed {
    new JWTService {
      override def createToken(user: User): Task[UserToken] =
        ZIO.succeed(UserToken(user.id, user.email, "BIG ACCESS", Long.MaxValue))
      override def verifyToken(token: String): Task[UserID] =
        ZIO.succeed(UserID(daniel.id, daniel.email))
    }
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("UserServiceSpec")(
      test("create and validate a user") {
        for {
          service <- ZIO.service[UserService]
          user    <- service.registerUser(daniel.email, "rockthejvm")
          valid   <- service.verifyPassword(daniel.email, "rockthejvm")
        } yield assertTrue(valid && user.email == daniel.email)
      },
      test("validate correct credentials") {
        for {
          service <- ZIO.service[UserService]
          valid   <- service.verifyPassword(daniel.email, "rockthejvm")
        } yield assertTrue(valid)
      },
      test("invalidate incorrect credentials") {
        for {
          service <- ZIO.service[UserService]
          valid   <- service.verifyPassword(daniel.email, "somethingelse")
        } yield assertTrue(!valid)
      },
      test("invalidate non-existent user") {
        for {
          service <- ZIO.service[UserService]
          valid   <- service.verifyPassword("someone@gmail.com", "somethingelse")
        } yield assertTrue(!valid)
      },
      test("update password") {
        for {
          service  <- ZIO.service[UserService]
          newUser  <- service.updatePassword(daniel.email, "rockthejvm", "scalarulez")
          oldValid <- service.verifyPassword(daniel.email, "rockthejvm")
          newValid <- service.verifyPassword(daniel.email, "scalarulez")
        } yield assertTrue(newValid && !oldValid)
      },
      test("delete non-existent user should fail") {
        for {
          service <- ZIO.service[UserService]
          err     <- service.deleteUser("someone@gmail.com", "something").flip
        } yield assertTrue(err.isInstanceOf[RuntimeException])
      },
      test("delete with incorrect credentials fail") {
        for {
          service <- ZIO.service[UserService]
          err     <- service.deleteUser(daniel.email, "something").flip
        } yield assertTrue(err.isInstanceOf[RuntimeException])
      },
      test("delete user") {
        for {
          service <- ZIO.service[UserService]
          user    <- service.deleteUser(daniel.email, "rockthejvm")
        } yield assertTrue(user.email == daniel.email)
      }
    ).provide(
      UserServiceLive.layer,
      stubJwtLayer,
      stubRepoLayer,
      stubEmailsLayer,
      stubTokenRepoLayer
    )
}
