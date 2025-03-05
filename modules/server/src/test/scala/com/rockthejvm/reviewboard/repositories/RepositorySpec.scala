package com.rockthejvm.reviewboard.repositories

import zio.*
import zio.test.*

import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource
import java.sql.SQLException

trait RepositorySpec {

  val initScript: String

  // test containers
  // spawn a Postgres instance on Docker just for the test
  private def createContainer() = {
    val container: PostgreSQLContainer[Nothing] =
      PostgreSQLContainer("postgres").withInitScript(
        initScript
      )
    container.start()
    container
  }

  // create a DataSource to connect to the Postgres
  private def createDataSource(container: PostgreSQLContainer[Nothing]): DataSource = {
    val dataSource = new PGSimpleDataSource()
    dataSource.setUrl(container.getJdbcUrl())
    dataSource.setUser(container.getUsername())
    dataSource.setPassword(container.getPassword())
    dataSource
  }

  // use the DataSource (as a ZLayer) to build the Quill instance (as a ZLayer)
  val dataSourceLayer = ZLayer {
    for {
      container <- ZIO.acquireRelease(ZIO.attempt(createContainer()))(container =>
        ZIO.attempt(container.stop()).ignoreLogged
      )
      dataSource <- ZIO.attempt(createDataSource(container))
    } yield dataSource
  }
}
