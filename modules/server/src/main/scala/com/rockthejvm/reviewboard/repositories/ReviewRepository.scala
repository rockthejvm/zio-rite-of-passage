package com.rockthejvm.reviewboard.repositories

import zio.*
import io.getquill.*
import io.getquill.jdbczio.Quill

import com.rockthejvm.reviewboard.domain.data.*
import java.time.Instant

trait ReviewRepository {
  def create(review: Review): Task[Review]
  def getById(id: Long): Task[Option[Review]]
  def getByCompanyId(id: Long): Task[List[Review]]
  def getByUserId(userId: Long): Task[List[Review]]
  def update(id: Long, op: Review => Review): Task[Review]
  def delete(id: Long): Task[Review]
  def getSummary(companyId: Long): Task[Option[ReviewSummary]]
  def insertSummary(companyId: Long, summary: String): Task[ReviewSummary]
}

class ReviewRepositoryLive private (quill: Quill.Postgres[SnakeCase]) extends ReviewRepository {
  import quill.*

  inline given reviewSchema: SchemaMeta[Review] =
    schemaMeta[Review]("reviews")
  inline given reviewInsertMeta: InsertMeta[Review] =
    insertMeta[Review](_.id, _.created, _.updated)
  inline given reviewUpdateMeta: UpdateMeta[Review] =
    updateMeta[Review](_.id, _.companyId, _.userId, _.created)

  inline given reviewSummarySchema: SchemaMeta[ReviewSummary] =
    schemaMeta[ReviewSummary]("review_summaries")
  inline given reviewSummaryInsertMeta: InsertMeta[ReviewSummary] =
    insertMeta[ReviewSummary]()
  inline given reviewSummaryUpdateMeta: UpdateMeta[ReviewSummary] =
    updateMeta[ReviewSummary]()

  def create(review: Review): Task[Review] =
    run(query[Review].insertValue(lift(review)).returning(r => r))

  def getById(id: Long): Task[Option[Review]] =
    run(query[Review].filter(_.id == lift(id))).map(_.headOption)

  def getByCompanyId(companyId: Long): Task[List[Review]] =
    run(query[Review].filter(_.companyId == lift(companyId)))

  def getByUserId(userId: Long): Task[List[Review]] =
    run(query[Review].filter(_.userId == lift(userId)))

  def update(id: Long, op: Review => Review): Task[Review] =
    for {
      current <- getById(id).someOrFail(
        new RuntimeException(s"update review failed: missing id $id")
      )
      updated <- run(
        query[Review].filter(_.id == lift(id)).updateValue(lift(op(current))).returning(r => r)
      )
    } yield updated

  def delete(id: Long): Task[Review] =
    run(query[Review].filter(_.id == lift(id)).delete.returning(r => r))

  override def getSummary(companyId: Long): Task[Option[ReviewSummary]] =
    run(query[ReviewSummary].filter(_.companyId == lift(companyId))).map(_.headOption)

  override def insertSummary(companyId: Long, summary: String): Task[ReviewSummary] =
    getSummary(companyId).flatMap {
      case None =>
        run(
          query[ReviewSummary]
            .insertValue(lift(ReviewSummary(companyId, summary, Instant.now())))
            .returning(r => r)
        )
      case Some(_) =>
        run(
          query[ReviewSummary]
            .filter(_.companyId == lift(companyId))
            .updateValue(lift(ReviewSummary(companyId, summary, Instant.now())))
            .returning(r => r)
        )
    }
}

object ReviewRepositoryLive {
  val layer = ZLayer {
    ZIO.service[Quill.Postgres[SnakeCase.type]].map(quill => ReviewRepositoryLive(quill))
  }
}

object ReviewRepositoryPlayground extends ZIOAppDefault {
  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = {
    val program = for {
      repo     <- ZIO.service[ReviewRepository]
      _        <- repo.insertSummary(1, "This is a first summary")
      summary  <- repo.getSummary(1)
      _        <- Console.printLine(summary)
      _        <- repo.insertSummary(1, "This is the second summary")
      summary2 <- repo.getSummary(1)
      _        <- Console.printLine(summary2)
    } yield ()

    program.provide(
      ReviewRepositoryLive.layer,
      Repository.dataLayer
    )
  }
}
