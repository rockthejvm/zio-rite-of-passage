package com.rockthejvm.reviewboard.http.controllers

import sttp.tapir.server.ServerEndpoint
import zio.*

import com.rockthejvm.reviewboard.domain.data.*
import com.rockthejvm.reviewboard.http.responses.InviteResponse
import com.rockthejvm.reviewboard.http.endpoints.InviteEndpoints
import com.rockthejvm.reviewboard.services.{InviteService, JWTService, PaymentService}

class InviteController private (
    inviteService: InviteService,
    jwtService: JWTService,
    paymentService: PaymentService
) extends BaseController
    with InviteEndpoints {

  val addPack: ServerEndpoint[Any, Task] =
    addPackEndpoint
      .serverSecurityLogic[UserID, Task](token => jwtService.verifyToken(token).either)
      .serverLogic { token => req =>
        inviteService
          .addInvitePack(token.email, req.companyId)
          .map(_.toString)
          .either
      }

  val invite: ServerEndpoint[Any, Task] =
    inviteEndpoint
      .serverSecurityLogic[UserID, Task](token => jwtService.verifyToken(token).either)
      .serverLogic { token => req =>
        inviteService
          .sendInvites(token.email, req.companyId, req.emails)
          .map { nInvitesSent =>
            if (nInvitesSent == req.emails.size) InviteResponse("ok", nInvitesSent)
            else InviteResponse("partial success", nInvites = nInvitesSent)
          }
          .either
      }

  val getByUserId: ServerEndpoint[Any, Task] =
    getByUserIdEndpoint
      .serverSecurityLogic[UserID, Task](token => jwtService.verifyToken(token).either)
      .serverLogic { token => _ =>
        inviteService.getByUserName(token.email).either
      }

  val addPackPromoted: ServerEndpoint[Any, Task] =
    addPackPromotedEndpoint
      .serverSecurityLogic[UserID, Task](token => jwtService.verifyToken(token).either)
      .serverLogic { token => req =>
        inviteService
          .addInvitePack(token.email, req.companyId)
          .flatMap { packId =>
            paymentService.createCheckoutSession(packId, token.email)
          } // Option[Session]
          .someOrFail(new RuntimeException("Cannot create payment checkout session"))
          .map(_.getUrl()) // the checkout session URL = the desired payload
          .either
      }

  val webhook: ServerEndpoint[Any, Task] =
    webhookEndpoint
      .serverLogic { (signature, payload) =>
        paymentService
          .handleWebhookEvent(
            signature,
            payload,
            packId => inviteService.activatePack(packId.toLong)
          )
          .unit
          .either
      }

  override val routes: List[ServerEndpoint[Any, Task]] =
    List(addPack, addPackPromoted, webhook, getByUserId, invite)
}

object InviteController {
  val makeZIO = for {
    inviteService  <- ZIO.service[InviteService]
    jwtService     <- ZIO.service[JWTService]
    paymentService <- ZIO.service[PaymentService]
  } yield new InviteController(inviteService, jwtService, paymentService)
}
