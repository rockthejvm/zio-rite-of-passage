package com.rockthejvm.reviewboard.repositories

import io.getquill.jdbczio.Quill
import io.getquill.SnakeCase

object Repository {
  def quillLayer =
    Quill.Postgres.fromNamingStrategy(SnakeCase)

  def dataSourceLayer =
    Quill.DataSource.fromPrefix("rockthejvm.db")

  val dataLayer =
    dataSourceLayer >>> quillLayer
}
