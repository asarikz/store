package controllers

import scala.collection.immutable
import play.api._
import db.DB
import libs.json.{JsObject, Json}
import play.api.mvc._

import java.sql.Connection
import models.{ItemInquiry}
import play.api.Play.current
import controllers.I18n.I18nAware
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Lang, Messages}
import play.api.data.validation.Constraints._
import constraints.FormConstraints._
import models.{CreateItemInquiry, CreateItemReservation, StoreUser, CreateItemInquiryReservation, ItemInquiryType, Site, ItemName, SiteItem, ItemId, LocaleInfo, LoginSession, ItemInquiryId, ItemInquiryField, ItemInquiryStatus}
import helpers.{ItemInquiryMail}

class ItemInquiryReserveBase extends Controller with I18nAware with NeedLogin with HasLogger {
  val idSubmitForm: Form[Long] = Form(
    single(
      "id" -> longNumber
    )
  )

  def itemInquiryForm(implicit lang: Lang): Form[CreateItemInquiryReservation] = Form(
    mapping(
      "siteId" -> longNumber,
      "itemId" -> longNumber,
      "name" -> text.verifying(nonEmpty, maxLength(128)),
      "email" -> text.verifying(emailConstraint: _*),
      "inquiryBody" -> text.verifying(nonEmpty, maxLength(8192))
    )(CreateItemInquiry.apply)(CreateItemInquiry.unapply)
  ).asInstanceOf[Form[CreateItemInquiryReservation]]

  def itemReservationForm(implicit lang: Lang): Form[CreateItemInquiryReservation] = Form(
    mapping(
      "siteId" -> longNumber,
      "itemId" -> longNumber,
      "name" -> text.verifying(nonEmpty, maxLength(128)),
      "email" -> text.verifying(emailConstraint: _*),
      "comment" -> text.verifying(minLength(0), maxLength(8192))
    )(CreateItemReservation.apply)(CreateItemReservation.unapply)
  ).asInstanceOf[Form[CreateItemInquiryReservation]]

  def startItemInquiry(
    siteId: Long, itemId: Long
  ) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    Ok(
      views.html.itemInquiry(itemInfo(siteId, itemId), inquiryStartForm(siteId, itemId, login.storeUser))
    )
  }

  def startItemReservation(
    siteId: Long, itemId: Long
  ) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    Ok(
      views.html.itemReservation(itemInfo(siteId, itemId), reservationStartForm(siteId, itemId, login.storeUser))
    )
  }

  def inquiryStartForm(
    siteId: Long, itemId: Long, user: StoreUser
  )(
    implicit login: LoginSession
  ): Form[_ <: CreateItemInquiryReservation] = itemInquiryForm.fill(
    CreateItemInquiry(
      siteId, itemId,
      user.fullName,
      user.email, ""
    )
  )

  def reservationStartForm(
    siteId: Long, itemId: Long, user: StoreUser
  )(
    implicit login: LoginSession
  ): Form[_ <: CreateItemInquiryReservation] = itemReservationForm.fill(
    CreateItemReservation(
      siteId, itemId,
      user.fullName,
      user.email, ""
    ).asInstanceOf[CreateItemInquiryReservation]
  )

  def itemInfo(
    siteId: Long, itemId: Long
  )(
    implicit lang: Lang
  ): (Site, ItemName) = DB.withConnection { implicit conn =>
    SiteItem.getWithSiteAndItem(siteId, ItemId(itemId), LocaleInfo.getDefault)
  }.get

  def amendReservationForm(
    rec: ItemInquiry, fields: immutable.Map[Symbol, String]
  ): Form[_ <: CreateItemInquiryReservation] = itemReservationForm.fill(
    CreateItemReservation(
      rec.siteId, rec.itemId.id,
      rec.submitUserName,
      rec.email,
      fields('Message)
    )
  )

  def amendItemReservationStart(inqId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    DB.withConnection { implicit conn =>
      val id = ItemInquiryId(inqId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)

      Ok(
        views.html.amendItemReservation(
          id,
          itemInfo(rec.siteId, rec.itemId.id),
          amendReservationForm(rec, fields)
        )
      )
    }
  }

  def amendItemReservation(inqId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    val id = ItemInquiryId(inqId)
    val (rec: ItemInquiry, fields: immutable.Map[Symbol, String]) = DB.withConnection { implicit conn =>
      (ItemInquiry(id), ItemInquiryField(id))
    }

    itemReservationForm.bindFromRequest.fold(
      formWithErrors => {
        logger.error("Validation error in ItemInquiryReserveBase.amendItemReservation." + formWithErrors + ".")
        BadRequest(views.html.amendItemReservation(id, itemInfo(rec.siteId, rec.itemId.id), formWithErrors))
      },
      info => DB.withTransaction { implicit conn =>
        info.update(id)
        Ok(
          views.html.itemReservationConfirm(
            ItemInquiry(id), ItemInquiryField(id), itemInfo(rec.siteId, rec.itemId.id), idSubmitForm.fill(inqId)
          )
        )
      }
    )
  }

  def confirmItemInquiry(
    siteId: Long, itemId: Long
  ) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    itemInquiryForm.bindFromRequest.fold(
      formWithErrors => {
        logger.error("Validation error in ItemInquiryReserveBase.submitItemInquiry." + formWithErrors + ".")
        BadRequest(views.html.itemInquiry(itemInfo(siteId, itemId), formWithErrors))
      },
      info => DB.withConnection { implicit conn =>
        val rec: ItemInquiry = info.save(login.storeUser)
        Redirect(routes.ItemInquiryReserve.submitItemInquiryStart(rec.id.get.id))
      }
    )
  }

  def amendInquiryForm(
    rec: ItemInquiry, fields: immutable.Map[Symbol, String]
  ): Form[_ <: CreateItemInquiryReservation] = itemReservationForm.fill(
    CreateItemInquiry(
      rec.siteId, rec.itemId.id,
      rec.submitUserName,
      rec.email,
      fields('Message)
    )
  )

  def amendItemInquiryStart(inqId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    DB.withConnection { implicit conn =>
      val id = ItemInquiryId(inqId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)

      Ok(
        views.html.amendItemInquiry(
          id,
          itemInfo(rec.siteId, rec.itemId.id),
          amendInquiryForm(rec, fields)
        )
      )
    }
  }

  def amendItemInquiry(inqId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    val id = ItemInquiryId(inqId)
    val (rec: ItemInquiry, fields: immutable.Map[Symbol, String]) = DB.withConnection { implicit conn =>
      (ItemInquiry(id), ItemInquiryField(id))
    }

    itemInquiryForm.bindFromRequest.fold(
      formWithErrors => {
        logger.error("Validation error in ItemInquiryReserveBase.amendItemInquiry." + formWithErrors + ".")
        BadRequest(
          views.html.amendItemInquiry(
            id,
            itemInfo(rec.siteId, rec.itemId.id),
            formWithErrors
          )
        )
      },
      info => DB.withTransaction { implicit conn =>
        info.update(id)
        Ok(
          views.html.itemReservationConfirm(
            rec, fields, itemInfo(rec.siteId, rec.itemId.id), idSubmitForm.fill(inqId)
          )
        )
      }
    )
  }

  def confirmItemReservation(
    siteId: Long, itemId: Long
  ) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    itemReservationForm.bindFromRequest.fold(
      formWithErrors => {
        logger.error("Validation error in ItemInquiryReserveBase.confirmItemReservation." + formWithErrors + ".")
        BadRequest(views.html.itemReservation(itemInfo(siteId, itemId), formWithErrors))
      },
      info => DB.withConnection { implicit conn =>
        val rec: ItemInquiry = info.save(login.storeUser)
        Redirect(routes.ItemInquiryReserve.submitItemReservationStart(rec.id.get.id))
      }
    )
  }

  def submitItemInquiryStart(inquiryId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    DB.withConnection { implicit conn =>
      val id = ItemInquiryId(inquiryId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)
      Ok(
        views.html.itemInquiryConfirm(
          rec, fields, itemInfo(rec.siteId, rec.itemId.id), idSubmitForm.fill(inquiryId)
        )
      )
    }
  }

  def submitItemInquiry(inquiryId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    idSubmitForm.bindFromRequest.fold(
      formWithErrors => DB.withConnection { implicit conn =>
        val id = ItemInquiryId(inquiryId)
        val rec = ItemInquiry(id)
        val fields = ItemInquiryField(id)
        logger.error("Validation error in ItemInquiryReserveBase.submitItemInquiry." + formWithErrors + ".")
        BadRequest(
          views.html.itemInquiryConfirm(
            rec, fields, itemInfo(rec.siteId, rec.itemId.id), idSubmitForm.fill(inquiryId)
          )
        )
      },
      id => DB.withConnection { implicit conn =>
        if (ItemInquiry.changeStatus(ItemInquiryId(id), ItemInquiryStatus.SUBMITTED) == 0) {
          throw new Error("Record update fail id = " + id)
        }
        Redirect(routes.Application.index).flashing("message" -> Messages("itemInquirySubmit"))
      }
    )
  }

  def submitItemReservationStart(inquiryId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    DB.withConnection { implicit conn =>
      val id = ItemInquiryId(inquiryId)
      val rec = ItemInquiry(id)
      val fields = ItemInquiryField(id)
      Ok(
        views.html.itemReservationConfirm(
          rec, fields, itemInfo(rec.siteId, rec.itemId.id), idSubmitForm.fill(inquiryId)
        )
      )
    }
  }

  def submitItemReservation(inquiryId: Long) = NeedAuthenticated { implicit request =>
    implicit val login = request.user

    idSubmitForm.bindFromRequest.fold(
      formWithErrors => DB.withConnection { implicit conn =>
        val id = ItemInquiryId(inquiryId)
        val rec = ItemInquiry(id)
        val fields = ItemInquiryField(id)
        logger.error("Validation error in ItemInquiryReserveBase.submitItemReservation." + formWithErrors + ".")
        BadRequest(
          views.html.itemReservationConfirm(
            rec, fields, itemInfo(rec.siteId, rec.itemId.id), idSubmitForm.fill(inquiryId)
          )
        )
      },
      longId => DB.withConnection { implicit conn =>
        val id: ItemInquiryId = ItemInquiryId(longId)
        val inquiry: ItemInquiry = ItemInquiry(id)
        val fields: immutable.Map[Symbol, String] = ItemInquiryField(id)

        ItemInquiryMail.send(login.storeUser, inquiry, fields, LocaleInfo.getDefault)

        if (ItemInquiry.changeStatus(id, ItemInquiryStatus.SUBMITTED) == 0) {
          throw new Error("Record update fail id = " + id)
        }
        Redirect(routes.Application.index).flashing("message" -> Messages("itemReservationSubmit"))
      }
    )
  }
}
