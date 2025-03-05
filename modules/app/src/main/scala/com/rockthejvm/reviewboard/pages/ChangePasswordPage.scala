package com.rockthejvm.reviewboard.pages

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import zio.*

import org.scalajs.dom.html.Element
import com.raquo.laminar.nodes.ReactiveHtmlElement

import com.rockthejvm.reviewboard.core.ZJS.*
import com.rockthejvm.reviewboard.core.*
import com.rockthejvm.reviewboard.http.requests.*

case class ChangePasswordState(
    password: String = "",
    newPassword: String = "",
    confirmPassword: String = "",
    upstreamStatus: Option[Either[String, String]] = None,
    override val showStatus: Boolean = false
) extends FormState {
  override val errorList: List[Option[String]] = List(
    Option.when(password.isEmpty)("Password can't be empty"),
    Option.when(newPassword.isEmpty)("New password can't be empty"),
    Option.when(newPassword != confirmPassword)("Passwords must match")
  ) ++ upstreamStatus.map(_.left.toOption).toList

  override val maybeSuccess: Option[String] =
    upstreamStatus.flatMap(_.toOption)
}

object ChangePasswordPage extends FormPage[ChangePasswordState]("Change Password") {

  override def basicState = ChangePasswordState()

  override def renderChildren(): List[ReactiveHtmlElement[Element]] =
    Session.getUserState
      .map(_.email)
      .map { email =>
        List(
          renderInput(
            "Password",
            "password-input",
            "password",
            true,
            "Your password",
            (s, p) => s.copy(password = p, showStatus = false, upstreamStatus = None)
          ),
          renderInput(
            "New Password",
            "new-password-input",
            "password",
            true,
            "New password",
            (s, p) => s.copy(newPassword = p, showStatus = false, upstreamStatus = None)
          ),
          renderInput(
            "Confirm New Password",
            "confirm-password-input",
            "password",
            true,
            "Confirm password",
            (s, p) => s.copy(confirmPassword = p, showStatus = false, upstreamStatus = None)
          ),
          button(
            `type` := "button",
            "Change Password",
            onClick.preventDefault.mapTo(stateVar.now()) --> submitter(email)
          )
        )
      }
      .getOrElse {
        List(
          div(
            cls := "centered-text",
            "Ouch! It seems you're not logged in yet."
          )
        )
      }

  def submitter(email: String) = Observer[ChangePasswordState] { state =>
    if (state.hasErrors) {
      stateVar.update(_.copy(showStatus = true))
    } else {
      useBackend(
        _.user.updatePasswordEndpoint(
          UpdatePasswordRequest(email, state.password, state.newPassword)
        )
      )
        .map { userResponse =>
          stateVar.update(
            _.copy(
              showStatus = true,
              upstreamStatus = Some(Right("Password successfully changed."))
            )
          )
        }
        .tapError { e =>
          ZIO.succeed {
            stateVar.update(_.copy(showStatus = true, upstreamStatus = Some(Left(e.getMessage))))
          }
        }
        .runJs
    }
  }
}
