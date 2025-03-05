package com.rockthejvm.reviewboard.services

import zio.*
import java.time.Instant
import com.rockthejvm.reviewboard.repositories.ReviewRepository
import com.rockthejvm.reviewboard.http.requests.CreateReviewRequest
import com.rockthejvm.reviewboard.domain.data.*
import com.rockthejvm.reviewboard.config.*

trait ReviewService {
  def create(request: CreateReviewRequest, userId: Long): Task[Review]
  def getById(id: Long): Task[Option[Review]]
  def getByCompanyId(companyId: Long): Task[List[Review]]
  def getByUserId(userId: Long): Task[List[Review]]
  def getSummary(companyId: Long): Task[Option[ReviewSummary]]
  def makeSummary(companyId: Long): Task[Option[ReviewSummary]]
}

class ReviewServiceLive private (
    repo: ReviewRepository,
    openaiService: OpenAIService,
    config: SummaryConfig
) extends ReviewService {
  override def create(request: CreateReviewRequest, userId: Long): Task[Review] =
    repo.create(
      Review(
        id = -1L,
        companyId = request.companyId,
        management = request.management,
        culture = request.culture,
        salary = request.salary,
        benefits = request.benefits,
        wouldRecommend = request.wouldRecommend,
        review = request.review,
        userId = userId,
        created = Instant.now(),
        updated = Instant.now()
      )
    )

  override def getById(id: Long): Task[Option[Review]] =
    repo.getById(id)
  override def getByCompanyId(companyId: Long): Task[List[Review]] =
    repo.getByCompanyId(companyId)
  override def getByUserId(userId: Long): Task[List[Review]] =
    repo.getByUserId(userId)

  override def getSummary(companyId: Long): Task[Option[ReviewSummary]] =
    repo.getSummary(companyId)

  override def makeSummary(companyId: Long): Task[Option[ReviewSummary]] =
    getByCompanyId(companyId)
      .flatMap(list => Random.shuffle(list))
      .map(_.take(config.nSelected))
      .flatMap { reviews =>
        val currentSummary: Task[Option[String]] =
          if (reviews.size < config.minReviews)
            ZIO.succeed(
              Some(s"Need to have at least ${config.minReviews} reviews to generate a summary.")
            )
          else
            buildPrompt(reviews).flatMap(openaiService.getCompletion)

        currentSummary.flatMap {
          case None          => ZIO.none
          case Some(summary) => repo.insertSummary(companyId, summary).map(Some(_))
        }
      }

  private def buildPrompt(reviews: List[Review]): Task[String] = ZIO.succeed {
    "You have the following reviews about a company:" +
      reviews.zipWithIndex
        .map {
          case (
                Review(
                  _,
                  _,
                  _,
                  management,
                  culture,
                  salary,
                  benefits,
                  wouldRecommend,
                  review,
                  _,
                  _
                ),
                index
              ) =>
            s"""
          Review ${index + 1}:
            Management: $management stars / 5
            Culture: $culture stars / 5
            Salary: $salary stars / 5
            Benefits: $benefits stars / 5
            Net promoter score: $wouldRecommend stars / 5
            Content: "$review"
          """
        }
        .mkString("\n") +
      "Make a summary of all these reviews in at most one paragraph."
  }

}

object ReviewServiceLive {
  val layer = ZLayer {
    for {
      repo          <- ZIO.service[ReviewRepository]
      openaiService <- ZIO.service[OpenAIService]
      config        <- ZIO.service[SummaryConfig]
    } yield new ReviewServiceLive(repo, openaiService, config)
  }

  val configuredLayer =
    Configs.makeLayer[SummaryConfig]("rockthejvm.summaries") >>> layer

}
