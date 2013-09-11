package models

import org.specs2.mutable._

import anorm._
import anorm.{NotAssigned, Pk}
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import play.api.db.DB
import play.api.Play.current
import anorm.Id
import java.util.Locale

import java.sql.Date.{valueOf => date}

class ItemSpec extends Specification {
  implicit def date2milli(d: java.sql.Date) = d.getTime

  "Item" should {
    "List item when empty." in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        TestHelper.removePreloadedRecords()

        Item.listBySiteId(siteId = 1, locale = LocaleInfo.Ja, queryString = "foo") === List()
      }
    }

    "Item name." in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        TestHelper.removePreloadedRecords()
        
        val cat1 = Category.createNew(
          Map(LocaleInfo.Ja -> "植木", LocaleInfo.En -> "Plant")
        )
        val item1 = Item.createNew(cat1)
        val names = ItemName.createNew(item1, Map(LocaleInfo.Ja -> "杉", LocaleInfo.En -> "Cedar"))

        names.size === 2
        names(LocaleInfo.Ja) === ItemName(LocaleInfo.Ja.id, item1.id.get, "杉")
        names(LocaleInfo.En) === ItemName(LocaleInfo.En.id, item1.id.get, "Cedar")

        val map = ItemName.list(item1)
        map.size === 2
        map(LocaleInfo.Ja) === ItemName(LocaleInfo.Ja.id, item1.id.get, "杉")
        map(LocaleInfo.En) === ItemName(LocaleInfo.En.id, item1.id.get, "Cedar")
      }
    }

    "item price." in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        TestHelper.removePreloadedRecords()

        val cat1 = Category.createNew(
          Map(LocaleInfo.Ja -> "植木", LocaleInfo.En -> "Plant")
        )
        val site1 = Site.createNew(LocaleInfo.Ja, "商店1")
        val item1 = Item.createNew(cat1)

        ItemPrice.get(site1, item1) === None

        val price1 = ItemPrice.createNew(site1, item1)
        val saved1 = ItemPrice.get(site1, item1).get
        saved1 === price1
      }
    }

    "Can get item price history." in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        TestHelper.removePreloadedRecords()

        val cat1 = Category.createNew(
          Map(LocaleInfo.Ja -> "植木", LocaleInfo.En -> "Plant")
        )
        val site1 = Site.createNew(LocaleInfo.Ja, "商店1")
        val item1 = Item.createNew(cat1)
        val price1 = ItemPrice.createNew(site1, item1)
        val tax = Tax.createNew()
      
        import java.sql.Date.{valueOf => date}
        implicit def date2milli(d: java.sql.Date) = d.getTime

        ItemPriceHistory.createNew(
          price1, tax, CurrencyInfo.Jpy, BigDecimal(100), date("2013-01-02")
        )
        ItemPriceHistory.createNew(
          price1, tax, CurrencyInfo.Jpy, BigDecimal(200), date("9999-12-31")
        )

        ItemPriceHistory.at(price1.id.get, date("2013-01-01")).unitPrice === BigDecimal(100)
        ItemPriceHistory.at(price1.id.get, date("2013-01-02")).unitPrice === BigDecimal(200)
        ItemPriceHistory.at(price1.id.get, date("2013-01-03")).unitPrice === BigDecimal(200)
      }
    }

    "Can get metadata" in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        TestHelper.removePreloadedRecords()

        val cat1 = Category.createNew(
          Map(LocaleInfo.Ja -> "植木", LocaleInfo.En -> "Plant")
        )
        val item1 = Item.createNew(cat1)
        val item2 = Item.createNew(cat1)

        ItemNumericMetadata.createNew(item1, MetadataType.HEIGHT, 100)
        ItemNumericMetadata.createNew(item1, MetadataType.STOCK, 200)
        
        ItemNumericMetadata.createNew(item2, MetadataType.HEIGHT, 1000)
        ItemNumericMetadata.createNew(item2, MetadataType.STOCK, 2000)

        ItemNumericMetadata(item1, MetadataType.HEIGHT).metadata === 100
        ItemNumericMetadata(item1, MetadataType.STOCK).metadata === 200

        ItemNumericMetadata(item2, MetadataType.HEIGHT).metadata === 1000
        ItemNumericMetadata(item2, MetadataType.STOCK).metadata === 2000
      }
    }

    "Can get all metadata at once" in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        TestHelper.removePreloadedRecords()

        val cat1 = Category.createNew(
          Map(LocaleInfo.Ja -> "植木", LocaleInfo.En -> "Plant")
        )
        val item1 = Item.createNew(cat1)
        val item2 = Item.createNew(cat1)

        ItemNumericMetadata.createNew(item1, MetadataType.HEIGHT, 100)
        ItemNumericMetadata.createNew(item1, MetadataType.STOCK, 200)
        
        ItemNumericMetadata.createNew(item2, MetadataType.HEIGHT, 1000)
        ItemNumericMetadata.createNew(item2, MetadataType.STOCK, 2000)

        val map1 = ItemNumericMetadata.all(item1)
        map1.size === 2
        map1(MetadataType.HEIGHT).metadata === 100
        map1(MetadataType.STOCK).metadata === 200

        val map2 = ItemNumericMetadata.all(item2)
        map2.size === 2
        map2(MetadataType.HEIGHT).metadata === 1000
        map2(MetadataType.STOCK).metadata === 2000
      }
    }

    def storeItems(tax: Tax, site1: Site, site2: Site) {
        import LocaleInfo._

        val cat1 = Category.createNew(
          Map(Ja -> "植木", En -> "Plant")
        )
        val cat2 = Category.createNew(
          Map(Ja -> "果樹", En -> "Fruit")
        )

        val item1 = Item.createNew(cat1)
        val item2 = Item.createNew(cat2)
        val item3 = Item.createNew(cat1)
        val item4 = Item.createNew(cat2)
        val item5 = Item.createNew(cat1)

        ItemName.createNew(item1, Map(Ja -> "杉", En -> "Cedar"))
        ItemName.createNew(item2, Map(Ja -> "梅", En -> "Ume"))
        ItemName.createNew(item3, Map(Ja -> "桜", En -> "Cherry"))
        ItemName.createNew(item4, Map(Ja -> "桃", En -> "Peach"))
        ItemName.createNew(item5, Map(Ja -> "もみじ", En -> "Maple"))

        SiteItem.createNew(site1, item1)
        SiteItem.createNew(site1, item3)
        SiteItem.createNew(site1, item5)

        SiteItem.createNew(site2, item2)
        SiteItem.createNew(site2, item4)

        ItemDescription.createNew(item1, site1, "杉説明")
        ItemDescription.createNew(item2, site2, "梅説明")
        ItemDescription.createNew(item3, site1, "桜説明")
        ItemDescription.createNew(item4, site2, "桃説明")
        ItemDescription.createNew(item5, site1, "もみじ説明")

        val price1 = ItemPrice.createNew(site1, item1)
        val price2 = ItemPrice.createNew(site2, item2)
        val price3 = ItemPrice.createNew(site1, item3)
        val price4 = ItemPrice.createNew(site2, item4)
        val price5 = ItemPrice.createNew(site1, item5)

        ItemPriceHistory.createNew(
          price1, tax, CurrencyInfo.Jpy, BigDecimal(100), date("2013-01-02")
        )
        ItemPriceHistory.createNew(
          price1, tax, CurrencyInfo.Jpy, BigDecimal(101), date("9999-12-31")
        )

        ItemPriceHistory.createNew(
          price2, tax, CurrencyInfo.Jpy, BigDecimal(300), date("2013-01-03")
        )
        ItemPriceHistory.createNew(
          price2, tax, CurrencyInfo.Jpy, BigDecimal(301), date("9999-12-31")
        )

        ItemPriceHistory.createNew(
          price3, tax, CurrencyInfo.Jpy, BigDecimal(500), date("2013-01-04")
        )
        ItemPriceHistory.createNew(
          price3, tax, CurrencyInfo.Jpy, BigDecimal(501), date("9999-12-31")
        )

        ItemPriceHistory.createNew(
          price4, tax, CurrencyInfo.Jpy, BigDecimal(1200), date("2013-01-05")
        )
        ItemPriceHistory.createNew(
          price4, tax, CurrencyInfo.Jpy, BigDecimal(1201), date("9999-12-31")
        )

        ItemPriceHistory.createNew(
          price5, tax, CurrencyInfo.Jpy, BigDecimal(2000), date("2013-01-06")
        )
        ItemPriceHistory.createNew(
          price5, tax, CurrencyInfo.Jpy, BigDecimal(2001), date("9999-12-31")
        )

        val height1 = ItemNumericMetadata.createNew(item1, MetadataType.HEIGHT, 100)
        val height2 = ItemNumericMetadata.createNew(item2, MetadataType.HEIGHT, 200)
        val height3 = ItemNumericMetadata.createNew(item3, MetadataType.HEIGHT, 300)
        val height4 = ItemNumericMetadata.createNew(item4, MetadataType.HEIGHT, 400)
        val height5 = ItemNumericMetadata.createNew(item5, MetadataType.HEIGHT, 500)
    }

    "List item by site." in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        import LocaleInfo._
        TestHelper.removePreloadedRecords()

        val tax = Tax.createNew()

        val site1 = Site.createNew(LocaleInfo.Ja, "商店1")
        val site2 = Site.createNew(LocaleInfo.Ja, "商店2")
        
        storeItems(tax, site1, site2)

        val time = date("2013-01-04").getTime
        val list1 = Item.listBySite(site1, LocaleInfo.Ja, "", now = time)
        list1.size === 3

        list1(0)._2.name === "もみじ"
        list1(1)._2.name === "杉"
        list1(2)._2.name === "桜"

        list1(0)._3.description === "もみじ説明"
        list1(1)._3.description === "杉説明"
        list1(2)._3.description === "桜説明"

        list1(0)._5.taxId === tax.id.get
        list1(0)._5.currency === CurrencyInfo.Jpy
        list1(0)._5.unitPrice === BigDecimal(2000)

        list1(1)._5.taxId === tax.id.get
        list1(1)._5.currency === CurrencyInfo.Jpy
        list1(1)._5.unitPrice === BigDecimal(101)

        list1(2)._5.taxId === tax.id.get
        list1(2)._5.currency === CurrencyInfo.Jpy
        list1(2)._5.unitPrice === BigDecimal(501)

        list1(0)._6(MetadataType.HEIGHT).metadata === 500
        list1(1)._6(MetadataType.HEIGHT).metadata === 100
        list1(2)._6(MetadataType.HEIGHT).metadata === 300
      }
    }

    "List item." in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        import LocaleInfo._
        TestHelper.removePreloadedRecords()

        val tax = Tax.createNew()

        val site1 = Site.createNew(LocaleInfo.Ja, "商店1")
        val site2 = Site.createNew(LocaleInfo.Ja, "商店2")
        
        storeItems(tax, site1, site2)

        val time = date("2013-01-04").getTime

        val list1 = Item.list(LocaleInfo.Ja, "", now = time)
        list1.size === 5

        list1(0)._2.name === "もみじ"
        list1(1)._2.name === "杉"
        list1(2)._2.name === "桃"
        list1(3)._2.name === "桜"
        list1(4)._2.name === "梅"

        list1(0)._3.description === "もみじ説明"
        list1(1)._3.description === "杉説明"
        list1(2)._3.description === "桃説明"
        list1(3)._3.description === "桜説明"
        list1(4)._3.description === "梅説明"

        list1(0)._5.taxId === tax.id.get
        list1(0)._5.currency === CurrencyInfo.Jpy
        list1(0)._5.unitPrice === BigDecimal(2000)

        list1(1)._5.taxId === tax.id.get
        list1(1)._5.currency === CurrencyInfo.Jpy
        list1(1)._5.unitPrice === BigDecimal(101)

        list1(2)._5.taxId === tax.id.get
        list1(2)._5.currency === CurrencyInfo.Jpy
        list1(2)._5.unitPrice === BigDecimal(1200)

        list1(3)._5.taxId === tax.id.get
        list1(3)._5.currency === CurrencyInfo.Jpy
        list1(3)._5.unitPrice === BigDecimal(501)

        list1(4)._5.taxId === tax.id.get
        list1(4)._5.currency === CurrencyInfo.Jpy
        list1(4)._5.unitPrice === BigDecimal(301)

        list1(0)._6(MetadataType.HEIGHT).metadata === 500
        list1(1)._6(MetadataType.HEIGHT).metadata === 100
        list1(2)._6(MetadataType.HEIGHT).metadata === 400
        list1(3)._6(MetadataType.HEIGHT).metadata === 300
        list1(4)._6(MetadataType.HEIGHT).metadata === 200
      }
    }
  }
}
