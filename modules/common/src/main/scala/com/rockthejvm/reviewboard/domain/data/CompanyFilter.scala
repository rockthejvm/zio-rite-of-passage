package com.rockthejvm.reviewboard.domain.data

import zio.json.JsonCodec

final case class CompanyFilter(
    locations: List[String] = List(),
    countries: List[String] = List(),
    industries: List[String] = List(),
    tags: List[String] = List()
) derives JsonCodec {
  val isEmpty = locations.isEmpty && countries.isEmpty && industries.isEmpty && tags.isEmpty
}

object CompanyFilter {
  val empty = CompanyFilter()
}
