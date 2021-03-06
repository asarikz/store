package controllers

import play.api.data.Forms._
import play.api._
import data.Form
import db.DB
import i18n.{Lang, Messages}
import play.api.mvc._
import play.filters.csrf.CSRF.Token._
import play.api.data.validation.Constraints._
import constraints.FormConstraints._
import models.{CreateUser, FirstSetup}
import controllers.I18n.I18nAware
import play.api.Play.current
import helpers.Cache

object Admin extends Controller with I18nAware with NeedLogin with HasLogger {
  import NeedLogin._

  def createUserForm[T <: CreateUser](
    apply: (String, String, Option[String], String, String, Seq[Option[String]], (String, String), String) => T,
    unapply: T => Option[(String, String, Option[String], String, String, Seq[Option[String]], (String, String), String)]
  )(implicit lang: Lang) = Form(
    mapping(
      "userName" -> text.verifying(userNameConstraint(): _*),
      "firstName" -> text.verifying(firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(lastNameConstraint: _*),
      "email" -> email.verifying(emailConstraint: _*),
      "supplementalEmails" -> seq(optional(email.verifying(optionalEmailConstraint))),
      "password" -> tuple(
        "main" -> text.verifying(passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "companyName" -> text.verifying(companyNameConstraint: _*)
    )(apply)(unapply)
  )

  def createNormalUserForm[T <: CreateUser](
    apply: (String, String, Option[String], String, String, Seq[Option[String]], (String, String), String) => T,
    unapply: T => Option[(String, String, Option[String], String, String, Seq[Option[String]], (String, String), String)]
  )(implicit lang: Lang) = Form(
    mapping(
      "userName" -> text.verifying(normalUserNameConstraint(): _*),
      "firstName" -> text.verifying(firstNameConstraint: _*),
      "middleName" -> optional(text),
      "lastName" -> text.verifying(lastNameConstraint: _*),
      "email" -> email.verifying(emailConstraint: _*),
      "supplementalEmails" -> seq(optional(email.verifying(optionalEmailConstraint))),
      "password" -> tuple(
        "main" -> text.verifying(passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "companyName" -> text.verifying(companyNameConstraint: _*)
    )(apply)(unapply)
  )

  def startFirstSetup = Action { implicit request => {
    Ok(views.html.admin.firstSetup(createUserForm(FirstSetup.fromForm, FirstSetup.toForm)))
  }}

  def firstSetup = Action { implicit request => {
    createUserForm(FirstSetup.fromForm, FirstSetup.toForm).bindFromRequest.fold(
      formWithErrors =>
        BadRequest(views.html.admin.firstSetup(formWithErrors)),
      firstSetup => DB.withConnection { implicit conn => {
        val createdUser = firstSetup.save
        Redirect(routes.Admin.index).flashing("message" -> Messages("welcome"))
      }}
    )
  }}
  
  def index = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    assumeAdmin(login) {
      Ok(views.html.admin.index())
    }
  }
}
