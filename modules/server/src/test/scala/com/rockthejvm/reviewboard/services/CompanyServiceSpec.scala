package com.rockthejvm.reviewboard.services

import zio.*
import zio.test.*

import com.rockthejvm.reviewboard.http.requests.CreateCompanyRequest
import com.rockthejvm.reviewboard.syntax.*
import com.rockthejvm.reviewboard.repositories.CompanyRepository
import com.rockthejvm.reviewboard.domain.data.Company
import com.rockthejvm.reviewboard.domain.data.CompanyFilter

object CompanyServiceSpec extends ZIOSpecDefault {

  val service = ZIO.serviceWithZIO[CompanyService]

  val stubRepoLayer = ZLayer.succeed(
    new CompanyRepository {
      val db = collection.mutable.Map[Long, Company]()

      override def create(company: Company): Task[Company] =
        ZIO.succeed {
          val nextId     = db.keys.maxOption.getOrElse(0L) + 1
          val newCompany = company.copy(id = nextId)
          db += (nextId -> newCompany)
          newCompany
        }

      override def update(id: Long, op: Company => Company): Task[Company] =
        ZIO.attempt {
          val company = db(id) // can crash
          db += (id -> op(company))
          company
        }

      override def delete(id: Long): Task[Company] =
        ZIO.attempt {
          val company = db(id)
          db -= id
          company
        }

      override def getById(id: Long): Task[Option[Company]] =
        ZIO.succeed(db.get(id))

      override def getBySlug(slug: String): Task[Option[Company]] =
        ZIO.succeed(db.values.find(_.slug == slug))

      override def get: Task[List[Company]] =
        ZIO.succeed(db.values.toList)

      override def uniqueAttributes: Task[CompanyFilter] = ZIO.succeed {
        val companies  = db.values
        val locations  = companies.flatMap(_.location.toList).toSet.toList
        val countries  = companies.flatMap(_.country.toList).toSet.toList
        val industries = companies.flatMap(_.industry.toList).toSet.toList
        val tags       = companies.flatMap(_.tags).toSet.toList
        CompanyFilter(locations, countries, industries, tags)
      }

      override def search(filter: CompanyFilter): Task[List[Company]] = ZIO.succeed {
        db.values.toList.filter { company =>
          filter.locations.toSet.intersect(company.location.toSet).nonEmpty ||
          filter.countries.toSet.intersect(company.country.toSet).nonEmpty ||
          filter.industries.toSet.intersect(company.industry.toSet).nonEmpty ||
          filter.tags.toSet.intersect(company.tags.toSet).nonEmpty
        }
      }
    }
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CompanyServiceSpec")(
      test("create") {
        val companyZIO = service(_.create(CreateCompanyRequest("Rock the JVM", "rockthejvm.com")))

        companyZIO.assert { company =>
          company.name == "Rock the JVM" &&
          company.url == "rockthejvm.com" &&
          company.slug == "rock-the-jvm"
        }
      },
      test("get by id") {
        val program = for {
          company    <- service(_.create(CreateCompanyRequest("Rock the JVM", "rockthejvm.com")))
          companyOpt <- service(_.getById(company.id))
        } yield (company, companyOpt)

        program.assert {
          case (company, Some(companyRes)) =>
            company.name == "Rock the JVM" &&
            company.url == "rockthejvm.com" &&
            company.slug == "rock-the-jvm" &&
            company == companyRes
          case _ => false
        }
      },
      test("get by slug") {
        val program = for {
          company    <- service(_.create(CreateCompanyRequest("Rock the JVM", "rockthejvm.com")))
          companyOpt <- service(_.getBySlug(company.slug))
        } yield (company, companyOpt)

        program.assert {
          case (company, Some(companyRes)) =>
            company.name == "Rock the JVM" &&
            company.url == "rockthejvm.com" &&
            company.slug == "rock-the-jvm" &&
            company == companyRes
          case _ => false
        }
      },
      test("get all") {
        val program = for {
          company   <- service(_.create(CreateCompanyRequest("Rock the JVM", "rockthejvm.com")))
          company2  <- service(_.create(CreateCompanyRequest("Google", "google.com")))
          companies <- service(_.getAll)
        } yield (company, company2, companies)

        program.assert { case (company, company2, companies) =>
          companies.toSet == Set(company, company2)
        }
      }
    ).provide(CompanyServiceLive.layer, stubRepoLayer)
}
