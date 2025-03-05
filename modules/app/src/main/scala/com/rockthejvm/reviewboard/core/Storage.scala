package com.rockthejvm.reviewboard.core

import org.scalajs.dom
import zio.json.*

object Storage {
  def set[A](key: String, value: A)(using encoder: JsonEncoder[A]) =
    dom.window.localStorage.setItem(key, value.toJson)

  def get[A](key: String)(using decoder: JsonDecoder[A]): Option[A] =
    Option(dom.window.localStorage.getItem(key))
      .filter(!_.isEmpty)
      .flatMap(value => decoder.decodeJson(value).toOption)

  def remove(key: String): Unit =
    dom.window.localStorage.removeItem(key)
}
