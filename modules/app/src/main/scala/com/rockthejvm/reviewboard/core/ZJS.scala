package com.rockthejvm.reviewboard.core

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

import sttp.client3.impl.zio.FetchZioBackend
import sttp.client3.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.model.Uri
import sttp.tapir.Endpoint
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.WebSockets
import zio.*

import com.rockthejvm.reviewboard.config.*
import scala.annotation.targetName

object ZJS {

  def useBackend =
    ZIO.serviceWithZIO[BackendClient]

  extension [E <: Throwable, A](zio: ZIO[BackendClient, E, A]) {
    def emitTo(eventBus: EventBus[A]) =
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.fork(
          zio
            .tap(value => ZIO.attempt(eventBus.emit(value)))
            .provide(BackendClientLive.configuredLayer)
        )
      }

    def toEventStream: EventStream[A] = {
      val bus = EventBus[A]()
      emitTo(bus)
      bus.events
    }

    def runJs =
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.fork(zio.provide(BackendClientLive.configuredLayer))
      }

  }

  extension [I, E <: Throwable, O](endpoint: Endpoint[Unit, I, E, O, Any])
    def apply(payload: I): RIO[BackendClient, O] =
      ZIO
        .service[BackendClient]
        .flatMap(_.endpointRequestZIO(endpoint)(payload))

  extension [I, E <: Throwable, O](endpoint: Endpoint[String, I, E, O, Any])
    @targetName("applySecure")
    def apply(payload: I): RIO[BackendClient, O] =
      ZIO
        .service[BackendClient]
        .flatMap(_.secureEndpointRequestZIO(endpoint)(payload))

}
