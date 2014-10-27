package functional

import play.api.test._
import play.api.test.Helpers._
import play.api.Play.current
import java.sql.Connection

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer, FakeApplication}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.db.DB
import play.api.test.TestServer
import play.api.test.FakeApplication
import java.sql.Date.{valueOf => date}
import controllers.ItemPictures
import java.nio.file.Files
import java.util
import java.nio.charset.Charset
import java.net.{HttpURLConnection, URL}
import java.io.{BufferedReader, InputStreamReader}
import java.text.SimpleDateFormat
import java.sql.Date.{valueOf => date}
import helpers.{ViewHelpers, QueryString}
import com.ruimo.scoins.Scoping._

class ItemMaintenanceSpec extends Specification {
  "Item maintenance should" should {
    "Create new item." in {
      val app = FakeApplication(additionalConfiguration = inMemoryDatabase())
      running(TestServer(3333, app), Helpers.HTMLUNIT) { browser => DB.withConnection { implicit conn =>
        implicit def date2milli(d: java.sql.Date) = d.getTime
        implicit val lang = Lang("ja")
        val user = loginWithTestUser(browser)
        val site = Site.createNew(LocaleInfo.Ja, "Store01")
        val cat = Category.createNew(Map(LocaleInfo.Ja -> "Cat01"))
        val tax = Tax.createNew
        val taxName = TaxName.createNew(tax, LocaleInfo.Ja, "tax01")
        val taxHis = TaxHistory.createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          "http://localhost:3333" + controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )

        browser.find("#siteId").find("option").getText() === "Store01"
        browser.find("#categoryId").find("option").getText() === "Cat01"
        browser.find("#taxId").find("option").getText() === "tax01"
        browser.fill("#description").`with`("Description01")

        browser.fill("#itemName").`with`("ItemName01")
        browser.fill("#price").`with`("1234")
        browser.fill("#costPrice").`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").getText() === Messages("itemIsCreated")

        val itemList = Item.list(None, LocaleInfo.Ja, QueryString()).records

        itemList.size === 1
        doWith(itemList.head) { item =>
          item._2.name === "ItemName01"
          item._3.description === "Description01"
          item._4.name === "Store01"
          item._5.unitPrice === BigDecimal("1234")
          item._5.costPrice === BigDecimal("2345")
          Coupon.isCoupon(item._1.id.get) === false
        }
      }}
    }

    "Create new coupon item." in {
      val app = FakeApplication(additionalConfiguration = inMemoryDatabase())
      running(TestServer(3333, app), Helpers.HTMLUNIT) { browser => DB.withConnection { implicit conn =>
        implicit def date2milli(d: java.sql.Date) = d.getTime
        implicit val lang = Lang("ja")
        val user = loginWithTestUser(browser)
        val site = Site.createNew(LocaleInfo.Ja, "Store01")
        val cat = Category.createNew(Map(LocaleInfo.Ja -> "Cat01"))
        val tax = Tax.createNew
        val taxName = TaxName.createNew(tax, LocaleInfo.Ja, "tax01")
        val taxHis = TaxHistory.createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          "http://localhost:3333" + controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )

        browser.find("#isCoupon").click()
        browser.find("#siteId").find("option").getText() === "Store01"
        browser.find("#categoryId").find("option").getText() === "Cat01"
        browser.find("#taxId").find("option").getText() === "tax01"
        browser.fill("#description").`with`("Description01")

        browser.fill("#itemName").`with`("ItemName01")
        browser.fill("#price").`with`("1234")
        browser.fill("#costPrice").`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").getText() === Messages("itemIsCreated")

        val itemList = Item.list(None, LocaleInfo.Ja, QueryString()).records

        itemList.size === 1
        doWith(itemList.head) { item =>
          item._2.name === "ItemName01"
          item._3.description === "Description01"
          item._4.name === "Store01"
          item._5.unitPrice === BigDecimal("1234")
          item._5.costPrice === BigDecimal("2345")
          Coupon.isCoupon(item._1.id.get) === true
        }
      }}
    }

    "Can edit item that has no handling stores." in {
      val app = FakeApplication(additionalConfiguration = inMemoryDatabase())
      running(TestServer(3333, app), Helpers.FIREFOX) { browser => DB.withConnection { implicit conn =>
        implicit def date2milli(d: java.sql.Date) = d.getTime
        implicit val lang = Lang("ja")
        val user = loginWithTestUser(browser)
        val site = Site.createNew(LocaleInfo.Ja, "Store01")
        val cat = Category.createNew(Map(LocaleInfo.Ja -> "Cat01"))
        val tax = Tax.createNew
        val taxName = TaxName.createNew(tax, LocaleInfo.Ja, "tax01")
        val taxHis = TaxHistory.createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          "http://localhost:3333" + controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )

        browser.find("#siteId").find("option").getText() === "Store01"
        browser.find("#categoryId").find("option").getText() === "Cat01"
        browser.find("#taxId").find("option").getText() === "tax01"
        browser.fill("#description").`with`("Description01")

        browser.fill("#itemName").`with`("ItemName01")
        browser.fill("#price").`with`("1234")
        browser.fill("#costPrice").`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").getText() === Messages("itemIsCreated")

        browser.goTo(
          "http://localhost:3333" + controllers.routes.ItemMaintenance.editItem(List("")).url + "&lang=" + lang.code
        )

        doWith(browser.find(".itemTableBody")) { tr =>
          tr.find(".itemTableItemName").getText === "ItemName01"
          tr.find(".itemTableSiteName").getText === "Store01"
          tr.find(".itemTablePrice").getText === ViewHelpers.toAmount(BigDecimal(1234))
        }

        val itemId = browser.find(".itemTableBody .itemTableItemId a").getText.toLong

        browser.goTo(
          "http://localhost:3333" + controllers.routes.ItemMaintenance.startChangeItem(itemId).url + "&lang=" + lang.code
        )

        // Now handling site becomes zero.
        browser.find(".deleteHandlingSiteButton").click()

        browser.goTo(
          "http://localhost:3333" + controllers.routes.ItemMaintenance.editItem(List("")).url + "&lang=" + lang.code
        )

        doWith(browser.find(".itemTableBody")) { tr =>
          tr.find(".itemTableItemName").getText === "ItemName01"
          tr.find(".itemTableSiteName").getText === "-"
          tr.find(".itemTablePrice").getText === "-"
        }
      }}
    }
  }
}
