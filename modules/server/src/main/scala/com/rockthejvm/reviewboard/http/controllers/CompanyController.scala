package com.rockthejvm.reviewboard.http.controllers

import zio.*
import collection.mutable

import com.rockthejvm.reviewboard.http.endpoints.CompanyEndpoints
import com.rockthejvm.reviewboard.domain.data.Company
import sttp.tapir.server.ServerEndpoint
import com.rockthejvm.reviewboard.services.CompanyService
import com.rockthejvm.reviewboard.domain.data.UserID
import com.rockthejvm.reviewboard.services.JWTService

class CompanyController private (service: CompanyService, jwtService: JWTService)
    extends BaseController
    with CompanyEndpoints {
  // in-memory "database"

  val create: ServerEndpoint[Any, Task] = createEndpoint
    .serverSecurityLogic[UserID, Task](token => jwtService.verifyToken(token).either)
    .serverLogic(_ => req => service.create(req).either)

  val getAll: ServerEndpoint[Any, Task] =
    getAllEndpoint.serverLogic { _ =>
      service.getAll.either
    }

  val getById: ServerEndpoint[Any, Task] =
    getByIdEndpoint.serverLogic { id =>
      ZIO
        .attempt(id.toLong)
        .flatMap(service.getById)
        .catchSome { case _: NumberFormatException =>
          service.getBySlug(id)
        }
        .either
    }

  val allFilters: ServerEndpoint[Any, Task] =
    allFiltersEndpoint.serverLogic { _ =>
      service.allFilters.either
    }

  val search: ServerEndpoint[Any, Task] =
    searchEndpoint.serverLogic { filter =>
      service.search(filter).either
    }

  override val routes: List[ServerEndpoint[Any, Task]] =
    List(create, getAll, allFilters, search, getById)
}

object CompanyController {
  val makeZIO = for {
    companyService <- ZIO.service[CompanyService]
    jwtService     <- ZIO.service[JWTService]
  } yield new CompanyController(companyService, jwtService)
}
