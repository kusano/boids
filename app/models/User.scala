package models

import anorm._
import anorm.SqlParser._
import java.util.Date
import play.api.db.DB
import play.api.Play.current
import java.security.MessageDigest

case class User(
   id: Option[Long] = None,
   name: String,
   password: String,
   resetKey: String,
   mailbox: String,
   popId: String,
   popPassword: String,
   createdAt: Option[Date] = None,
   modifiedAt: Option[Date] = None,
   active: Boolean
)

object User {

  val simple = {
      get[Long]("id") ~
      get[String]("name") ~
      get[String]("password") ~
      get[String]("reset_key") ~
      get[String]("mailbox") ~
      get[String]("pop_id") ~
      get[String]("pop_password") ~
      get[Date]("created_at") ~
      get[Date]("modified_at") ~
      get[Int]("active") map {
      case id ~ name ~ password ~ resetKey ~ mailbox ~ popId ~ popPassword ~ createdAt ~ modifiedAt ~ active =>
        User(Some(id), name, password, resetKey, mailbox, popId, popPassword, Some(createdAt), Some(modifiedAt),
          active!=0)
    }
  }

  def findByName(name: String): Option[User] = {
    DB.withConnection { implicit c =>
      SQL("select * from user where name={name}")
        .on('name -> name)
        .as(User.simple.singleOpt)
    }
  }

  def insert(user: User): Boolean = {
    DB.withConnection { implicit c =>
      SQL(
        """
          |insert into user(
          | name,
          | password,
          | reset_key,
          | mailbox,
          | pop_id,
          | pop_password,
          | created_at,
          | modified_at,
          | active
          |) values (
          | {name},
          | {password},
          | {reset_key},
          | {mailbox},
          | {pop_id},
          | {pop_password},
          | {created_at},
          | {modified_at},
          | {active}
          |)
        """.stripMargin
      ).on(
          'name -> user.name,
          'password -> user.password,
          'reset_key -> user.resetKey,
          'mailbox -> user.mailbox,
          'pop_id -> user.popId,
          'pop_password -> user.popPassword,
          'created_at -> new Date(),
          'modified_at -> new Date(),
          'active -> (if (user.active) 1 else 0)
        ).executeUpdate() == 1
    }
  }

  def update(user: User): Boolean = {
    DB.withConnection { implicit c =>
      SQL(
        """
          |update user set
          | name = {name},
          | password = {password},
          | reset_key = {reset_key},
          | mailbox = {mailbox},
          | pop_id = {pop_id},
          | pop_password = {pop_password},
          | modified_at = {modified_at},
          | active = {active}
          |where id={id}
        """.stripMargin
      ).on(
          'id -> user.id.get,
          'name -> user.name,
          'password -> user.password,
          'reset_key -> user.resetKey,
          'mailbox -> user.mailbox,
          'pop_id -> user.popId,
          'pop_password -> user.popPassword,
          'modified_at -> new Date(),
          'active -> (if (user.active) 1 else 0)
        ).executeUpdate() == 1
    }
  }

  def authenticate(name: String, password: String): Option[User] = {
    DB.withConnection { implicit c =>
      SQL("select * from user where name={name} and password={password}")
        .on('name -> name, 'password -> hashPassword(password))
        .as(User.simple.singleOpt)
    }
  }

  def findByResetKey(resetKey: String): Option[User] = {
    DB.withConnection { implicit c =>
      SQL("select * from user where reset_key={resetKey}")
        .on('resetKey -> hashPassword(resetKey))
        .as(User.simple.singleOpt)
    }
  }

  def hashPassword(password: String): String = {
    val digest = MessageDigest.getInstance("SHA-512")
    (0 until 1000).foldLeft(password) { (s, _) =>
      digest.digest(s.getBytes).map("%02x".format(_)).mkString
    }
  }
}
