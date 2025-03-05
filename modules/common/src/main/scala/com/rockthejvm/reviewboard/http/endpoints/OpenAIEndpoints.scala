package com.rockthejvm.reviewboard.http.endpoints

import sttp.tapir.*
import sttp.client3.*
import sttp.tapir.json.zio.*
import sttp.tapir.generic.auto.*

import com.rockthejvm.reviewboard.domain.errors.HttpError
import com.rockthejvm.reviewboard.http.requests.*
import com.rockthejvm.reviewboard.http.responses.*

trait OpenAIEndpoints extends BaseEndpoint {
  val completionsEndpoint =
    endpoint
      .errorOut(statusCode and plainBody[String])
      .mapErrorOut[Throwable](HttpError.decode)(HttpError.encode)
      .securityIn(auth.bearer[String]())
      .in("v1" / "chat" / "completions")
      .post
      .in(jsonBody[CompletionRequest])
      .out(jsonBody[CompletionResponse])
}
