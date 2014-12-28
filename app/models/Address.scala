package models

import anorm._
import anorm.SqlParser._
import java.util.Date
import play.api.db.DB
import play.api.Play.current
import java.security.MessageDigest

case class Address(
  address: String,
  user: Long,
  memo: String,
  createdAt: Option[Date] = None,
  modifiedAt: Option[Date] = None,
  active: Boolean
)

object Address {

  val simple = {
    get[String]("address") ~
    get[Long]("user") ~
    get[String]("memo") ~
    get[Date]("created_at") ~
    get[Date]("modified_at") ~
    get[Int]("active") map {
      case address ~ user ~ memo ~ createdAt ~ modifiedAt ~ active =>
        Address(address, user, memo, Some(createdAt), Some(modifiedAt), active!=0)
    }
  }

  def findByAddress(address: String): Option[Address] = {
    DB.withConnection { implicit c =>
      SQL("select * from address where address={address}")
        .on('address -> address)
        .as(Address.simple.singleOpt)
    }
  }

  def findByUser(user: User, filter: String, includeInactive: Boolean, orderBy: String, order: String,
                 offset: Int, limit: Int): Seq[Address] = {
    if (Seq("address", "memo", "created_at", "modified_at", "active").contains(orderBy) &&
        Seq("asc", "desc").contains(order)) {
      DB.withConnection { implicit c =>
        val sFilter = if (filter=="") ""
          else "and (concat(address,'@boids.info') like {filter} or memo like {filter})"
        val sActive = if (includeInactive) "" else "and active!=0"
        SQL(
          s"""
            |select * from address where
            |user = {user}
            |${sFilter}
            |${sActive}
            |order by ${orderBy} ${order}
            |limit {limit}
            |offset {offset}
          """.stripMargin)
          .on(
            'user -> user.id.get,
            'filter -> ("%" + filter.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"),
            'active -> (if (includeInactive) "1" else "active!=0"),
            'orderBy -> orderBy,
            'order -> order,
            'offset -> offset,
            'limit -> limit
          )
          .as(Address.simple *)
      }
    } else {
      Seq[Address]()
    }
  }

  def count(user: User): Long = {
    DB.withConnection { implicit c =>
      SQL("select count(*) as c from address where user = {user}")
        .on('user -> user.id.get)
        .apply()
        .head[Long]("c")
    }
  }

  def insert(address: Address): Boolean = {
    DB.withConnection { implicit c =>
      SQL(
        """
          |insert into address(
          | address,
          | user,
          | memo,
          | created_at,
          | modified_at,
          | active
          |) values (
          | {address},
          | {user},
          | {memo},
          | {created_at},
          | {modified_at},
          | {active}
          |)
        """.stripMargin
      ).on(
          'address -> address.address,
          'user -> address.user,
          'memo -> address.memo,
          'created_at -> new Date(),
          'modified_at -> new Date(),
          'active -> (if (address.active) 1 else 0)
        ).executeUpdate() == 1
    }
  }

  def update(address: Address): Boolean = {
    DB.withConnection { implicit c =>
      SQL(
        """
          |update address set
          | memo = {memo},
          | modified_at = {modified_at},
          | active = {active}
          |where address={address}
        """.stripMargin
      ).on(
          'address -> address.address,
          'memo -> address.memo,
          'modified_at -> new Date(),
          'active -> (if (address.active) 1 else 0)
        ).executeUpdate() == 1
    }
  }
}
