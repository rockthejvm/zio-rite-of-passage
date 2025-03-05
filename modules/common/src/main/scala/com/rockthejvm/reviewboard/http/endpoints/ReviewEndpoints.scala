package com.rockthejvm.reviewboard.http.endpoints

import sttp.tapir.*
import sttp.tapir.json.zio.*
import sttp.tapir.generic.auto.*

import com.rockthejvm.reviewboard.http.requests.CreateReviewRequest
import com.rockthejvm.reviewboard.domain.data.*

trait ReviewEndpoints extends BaseEndpoint {
  // post /reviews { CreateReviewRequest } - create review
  // returns a Review
  val createEndpoint =
    secureBaseEndpoint
      .tag("Reviews")
      .name("create")
      .description("Add a review for a company")
      .in("reviews")
      .post
      .in(jsonBody[CreateReviewRequest])
      .out(jsonBody[Review])

  // get /reviews/id - get review by id
  // returns Option[Review]
  val getByIdEndpoint =
    baseEndpoint
      .tag("Reviews")
      .name("getById")
      .description("Get a review by its id")
      .in("reviews" / path[Long]("id"))
      .get
      .out(jsonBody[Option[Review]])

  // get /reviews/company/id - get review by company id
  // returns List[Review]
  val getByCompanyIdEndpoint =
    baseEndpoint
      .tag("Reviews")
      .name("getByCompanyId")
      .description("Get reviews for a company")
      .in("reviews" / "company" / path[Long]("id"))
      .get
      .out(jsonBody[List[Review]])

  // summary endpoints
  val getSummaryEndpoint =
    baseEndpoint
      .tag("Reviews")
      .name("get summary by company id")
      .description("Get current review summary for a company id")
      .in("reviews" / "company" / path[Long]("id") / "summary")
      .get
      .out(jsonBody[Option[ReviewSummary]])

  val makeSummaryEndpoint =
    baseEndpoint
      .tag("Reviews")
      .name("generate summary by company id")
      .description("Trigger review summary creation for a company id")
      .in("reviews" / "company" / path[Long]("id") / "summary")
      .post
      .out(jsonBody[Option[ReviewSummary]])
}
