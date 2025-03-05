package com.rockthejvm.reviewboard.domain.data

import java.time.Instant
import zio.json.JsonCodec

final case class ReviewSummary(
    companyId: Long,
    contents: String,
    created: Instant
) derives JsonCodec
