package com.rockthejvm.reviewboard.services

import zio.*

import com.rockthejvm.reviewboard.domain.data.*
import com.rockthejvm.reviewboard.config.*
import com.rockthejvm.reviewboard.repositories.InviteRepository
import com.rockthejvm.reviewboard.repositories.CompanyRepository

trait InviteService {
  def getByUserName(userName: String): Task[List[InviteNamedRecord]]
  def sendInvites(userName: String, companyId: Long, receivers: List[String]): Task[Int]
  def addInvitePack(userName: String, companyId: Long): Task[Long]
  def activatePack(id: Long): Task[Boolean]
}

class InviteServiceLive private (
    inviteRepo: InviteRepository,
    companyRepo: CompanyRepository,
    emailService: EmailService,
    config: InvitePackConfig
) extends InviteService {
  override def getByUserName(userName: String): Task[List[InviteNamedRecord]] =
    inviteRepo.getByUserName(userName)

  // invariant: only one pack per user per company
  override def addInvitePack(userName: String, companyId: Long): Task[Long] =
    for {
      company <- companyRepo
        .getById(companyId)
        .someOrFail(
          new RuntimeException(s"Cannot invite to review: company $companyId doesn't exist")
        )
      currentPack <- inviteRepo.getInvitePack(userName, companyId)
      newPackId <- currentPack match {
        case None => inviteRepo.addInvitePack(userName, companyId, config.nInvites)
        case Some(_) =>
          ZIO.fail(new RuntimeException("You already have an active pack for this company"))
      }
    } yield newPackId

  override def sendInvites(userName: String, companyId: Long, receivers: List[String]): Task[Int] =
    for {
      company <- companyRepo
        .getById(companyId)
        .someOrFail(
          new RuntimeException(s"Cannot send invites: company $companyId doesn't exist")
        )
      nInvitesMarked <- inviteRepo.markInvites(userName, companyId, receivers.size)
      _ <- ZIO.collectAllPar(
        receivers
          .take(nInvitesMarked)
          .map(receiver => emailService.sendReviewInvite(userName, receiver, company))
      )
    } yield nInvitesMarked

  override def activatePack(id: Long): Task[Boolean] =
    inviteRepo.activatePack(id)
}

object InviteServiceLive {
  val layer = ZLayer {
    for {
      inviteRepo   <- ZIO.service[InviteRepository]
      companyRepo  <- ZIO.service[CompanyRepository]
      emailService <- ZIO.service[EmailService]
      config       <- ZIO.service[InvitePackConfig]
    } yield new InviteServiceLive(inviteRepo, companyRepo, emailService, config)
  }

  val configuredLayer =
    Configs.makeLayer[InvitePackConfig]("rockthejvm.invites") >>> layer
}
