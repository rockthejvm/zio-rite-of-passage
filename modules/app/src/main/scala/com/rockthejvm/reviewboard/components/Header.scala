package com.rockthejvm.reviewboard.components

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.codecs.*
import org.scalajs.dom

import com.rockthejvm.reviewboard.common.*
import com.rockthejvm.reviewboard.core.*
import com.rockthejvm.reviewboard.domain.data.UserToken

object Header {
  def apply() =
    div(
      cls := "container-fluid p-0",
      div(
        cls := "jvm-nav",
        div(
          cls := "container",
          navTag(
            cls := "navbar navbar-expand-lg navbar-light JVM-nav",
            div(
              cls := "container",
              renderLogo(),
              button(
                cls                                         := "navbar-toggler",
                `type`                                      := "button",
                htmlAttr("data-bs-toggle", StringAsIsCodec) := "collapse",
                htmlAttr("data-bs-target", StringAsIsCodec) := "#navbarNav",
                htmlAttr("aria-controls", StringAsIsCodec)  := "navbarNav",
                htmlAttr("aria-expanded", StringAsIsCodec)  := "false",
                htmlAttr("aria-label", StringAsIsCodec)     := "Toggle navigation",
                span(cls := "navbar-toggler-icon")
              ),
              div(
                cls    := "collapse navbar-collapse",
                idAttr := "navbarNav",
                ul(
                  cls := "navbar-nav ms-auto menu align-center expanded text-center SMN_effect-3",
                  children <-- Session.userState.signal.map(renderNavLinks)
                )
              )
            )
          )
        )
      )
    )

  private def renderLogo() =
    a(
      href := "/",
      cls  := "navbar-brand",
      img(
        cls := "home-logo",
        src := Constants.logoImage,
        alt := "Rock the JVM"
      )
    )

  // list of <li> tags
  // Companies, Log In, Sign up
  private def renderNavLinks(maybeUser: Option[UserToken]) = {
    val constantLinks = List(
      renderNavLink("Companies", "/companies")
    )

    val unauthedLinks = List(
      renderNavLink("Log In", "/login"),
      renderNavLink("Sign Up", "/signup")
    )

    val authedLinks = List(
      renderNavLink("Add Company", "/post"),
      renderNavLink("Profile", "/profile"),
      renderNavLink("Log Out", "/logout")
    )

    val customLinks =
      if (maybeUser.nonEmpty) authedLinks
      else unauthedLinks

    constantLinks ++ customLinks
  }

  private def renderNavLink(text: String, location: String) =
    li(
      cls := "nav-item",
      Anchors.renderNavLink(text, location, "nav-link jvm-item")
    )

}
