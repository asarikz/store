package helpers

import controllers.HasLogger
import play.api.Play
import scala.collection.immutable
import models.{ItemInquiry, ItemInquiryField, Site, OrderNotification, LocaleInfo, ItemName, SiteItem, StoreUser, ItemInquiryType}
import java.sql.Connection
import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.duration._
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer._

object ItemInquiryMail extends HasLogger {
  val disableMailer = Play.current.configuration.getBoolean("disable.mailer").getOrElse(false)
  val from = Play.current.configuration.getString("user.registration.email.from").get

  def send(
    user: StoreUser, inq: ItemInquiry, fields: immutable.Map[Symbol, String], locale: LocaleInfo
  )(
    implicit conn: Connection
  ) {
    val itemInfo: (Site, ItemName) = SiteItem.getWithSiteAndItem(inq.siteId, inq.itemId, locale).get

    sendToBuyer(user, locale, itemInfo, inq, fields)
    sendToStoreOwner(user, locale, itemInfo, inq, fields)
    sendToAdmin(user, locale, itemInfo, inq, fields)
  }

  def sendToBuyer(
    user: StoreUser, locale: LocaleInfo, itemInfo: (Site, ItemName), inq: ItemInquiry, fields: immutable.Map[Symbol, String]
  )(
    implicit conn: Connection
  ) {
    logger.info("Sending item inquiry for buyer sent to " + inq.email)
    val body = inq.inquiryType match {
      case ItemInquiryType.QUERY =>
        views.html.mail.itemInquiryForBuyer(user, itemInfo, inq, fields).toString
      case ItemInquiryType.RESERVATION => 
        views.html.mail.itemReservationForBuyer(user, itemInfo, inq, fields).toString
      case t =>
        throw new Error("Unknown inquiry type " + t)
    }
    if (! disableMailer) {
      Akka.system.scheduler.scheduleOnce(0.microsecond) {
        val mail = Email(
          subject = Messages(
            inq.inquiryType match {
              case ItemInquiryType.QUERY => "mail.item.inquiry.buyer.subject"
              case ItemInquiryType.RESERVATION => "mail.item.reservation.buyer.subject"
            }
          ).format(inq.id.get.id),
          to = Seq(inq.email),
          from = from,
          bodyText = Some(body)
        )
        MailerPlugin.send(mail)
        logger.info("Item inquiry notification for buyer sent to " + inq.email)
      }
    }
  }

  def sendToStoreOwner(
    user: StoreUser, locale: LocaleInfo, itemInfo: (Site, ItemName), inq: ItemInquiry, fields: immutable.Map[Symbol, String]
  )(
    implicit conn: Connection
  ) {
    OrderNotification.listBySite(inq.siteId).foreach { owner =>
      logger.info("Sending item inquiry to site owner " + itemInfo._1 + " sent to " + inq.email)
      val body = inq.inquiryType match {
        case ItemInquiryType.QUERY =>
          views.html.mail.itemInquiryForSiteOwner(user, itemInfo, inq, fields).toString
        case ItemInquiryType.RESERVATION => 
          views.html.mail.itemReservationForSiteOwner(user, itemInfo, inq, fields).toString
        case t =>
          throw new Error("Unknown inquiry type " + t)
      }
      if (! disableMailer) {
        Akka.system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages(
              inq.inquiryType match {
                case ItemInquiryType.QUERY => "mail.item.inquiry.site.owner.subject"
                case ItemInquiryType.RESERVATION => "mail.item.reservation.site.owner.subject"
              }
            ).format(inq.id.get.id),
            to = Seq(owner.email),
            from = from,
            bodyText = Some(body)
          )
          MailerPlugin.send(mail)
          logger.info("Item inquiry notification for site owner " + itemInfo._1 + " sent to " + inq.email)
        }
      }
    }
  }

  def sendToAdmin(
    user: StoreUser, locale: LocaleInfo, itemInfo: (Site, ItemName), inq: ItemInquiry, fields: immutable.Map[Symbol, String]
  )(
    implicit conn: Connection
  ) {
    if (! disableMailer) {
      OrderNotification.listAdmin.foreach { admin =>
        logger.info("Sending item inquiry for admin to " + admin.email)
        val body = inq.inquiryType match {
          case ItemInquiryType.QUERY =>
            views.html.mail.itemInquiryForAdmin(user, itemInfo, inq, fields).toString
          case ItemInquiryType.RESERVATION =>
            views.html.mail.itemReservationForAdmin(user, itemInfo, inq, fields).toString
        case t =>
          throw new Error("Unknown inquiry type " + t)
        }
        Akka.system.scheduler.scheduleOnce(0.microsecond) {
          val mail = Email(
            subject = Messages(
              inq.inquiryType match {
                case ItemInquiryType.QUERY => "mail.item.inquiry.site.owner.subject"
                case ItemInquiryType.RESERVATION => "mail.item.reservation.site.owner.subject"
              }
            ).format(inq.id.get.id),
            to = Seq(admin.email),
            from = from,
            bodyText = Some(body)
          )
          MailerPlugin.send(mail)
          logger.info("Item inquiry notification for admin to " + admin.email)
        }
      }
    }
    else {
      logger.info("Item inquiry notification mail is not sent since mailer is disabled.")
    }
  }
}
