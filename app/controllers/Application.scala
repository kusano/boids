package controllers

import java.text.SimpleDateFormat

import play.api._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.ws.WS
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future
import scala.util.Random
import java.security.SecureRandom
import models.{Address, User}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {

  def checkLogin(login: User => Result, notLogin: => Result)(implicit request: Request[AnyContent]) = {
    val name = request.session.get("id")
    if (name.isDefined) {
      val user = User.findByName(name.get)
      if (user.isDefined) {
        login(user.get)
      } else {
        notLogin
      }
    } else {
      notLogin
    }
  }

  def index = Action { implicit request =>
    checkLogin({ user =>
      Ok(views.html.index(Some(user)))
    }, {
      Ok(views.html.index(None))
    })
  }

  def topPage = Action { implicit request =>
    checkLogin({ user =>
      Ok(views.html.top(user))
    }, {
      Redirect(routes.Application.index)
    })
  }

  def showAccount = Action { implicit request =>
    checkLogin({ user =>
      Ok(views.html.account(user))
    }, {
      Redirect(routes.Application.index)
    })
  }

  def FAQ = Action { implicit request =>
    checkLogin({ user =>
      Ok(views.html.faq(Some(user)))
    }, {
      Ok(views.html.faq(None))
    })
  }

  def createAccount = Action.async { implicit request =>
    def f(): Future[Result] = {
      val form = request.body.asFormUrlEncoded.get

      if (form.getOrElse("agree", Seq(""))(0) == "") {
        return Future { Redirect(routes.Application.index).flashing("error" -> "規約に同意してください") }
      }
      if (form.getOrElse("g-recaptcha-response", Seq(""))(0) == "") {
        return Future { Redirect(routes.Application.index).flashing("error" -> "CAPTCHA認証に失敗しました") }
      }

      WS.url("https://www.google.com/recaptcha/api/siteverify").withQueryString(
        "secret" -> "6LeUpP8SAAAAADfgjDpx-noxCmuFZZw7NdL6EqT6",
        "response" -> form.getOrElse("g-recaptcha-response", Seq(""))(0)
      ).get().map { response =>
        if (!(response.json \ "success").as[Boolean]) {
          Redirect(routes.Application.index).flashing("error" -> "CAPTCHA認証に失敗しました")
        } else {
          val password = createRandomString(16)
          val resetKey = createRandomString(128)

          val user = User(
            name = createRandomString(16),
            password = User.hashPassword(password),
            resetKey = User.hashPassword(resetKey),
            mailbox = createRandomString(32, true),
            popId = createRandomString(16),
            popPassword = createRandomString(16),
            active = true
          )

          if (User.insert(user)) {
            Logger.info(s"Created user ${user.name} (${user.password}, ${user.resetKey})")

            val address = Address(
              address = createRandomString(24, true),
              user = User.findByName(user.name).get.id.get,
              memo = "",
              active = true
            )

            if (Address.insert(address)) {
              Logger.info(s"Created address ${address.address} for ${user.name}")
            } else {
              Logger.info(s"Failed to create address ${address.address} for ${user.name}")
            }
            Ok(views.html.newaccount(user, password, resetKey))
          } else {
            Logger.error("Failed to create user " + user.name)
            Redirect(routes.Application.index).flashing("error" -> "アカウントの作成に失敗しました")
          }
        }
      }
    }
    f()
  }

  def login = Action { implicit request =>
    Form(
      tuple(
        "id" -> text,
        "password" -> text
      ) verifying (result => result match {
        case (name, password) => User.authenticate(name, password).isDefined
      })
    ).bindFromRequest.fold(
      error => {
        Redirect(routes.Application.index).flashing(
          "error" -> "Invalid id or password",
          "id" -> error.data.getOrElse("id","")
        )
      },
      user => {
        val name = user._1
        Logger.info(s"Login ($name)")
        Redirect(routes.Application.topPage).withSession("id" -> name)
      }
    )
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index).withNewSession
  }

  def jsonError(message: String): Result = {
    Ok(JsObject(Seq(
      "success" -> JsBoolean(false),
      "error" -> JsString(message)
    )))
  }

  def createAddress = Action { implicit request =>
    checkLogin({ user =>

      val address = Address(
        address = createRandomString(24, true),
        user = user.id.get,
        memo = "",
        active = true
      )

      if (Address.count(user) >= Play.configuration.getInt("application.maxAddress").get) {
        jsonError("Too many addresses")
      } else {
        val result = Address.insert(address)

        if (result) {
          Logger.info("User " + user.name + " created address " + address.address)
          Ok(JsObject(Seq(
            "success" -> JsBoolean(true),
            "address" -> JsString(address.address)
          )))
        } else {
          Logger.error("User " + user.name + " failed to create address " + address.address)
          jsonError("Failed to create address")
        }
      }
    }, {
      jsonError("Login is needed")
    })
  }

  def updateAddress = Action { implicit request =>
    checkLogin({ user =>
      def func(): Result = {
        val form = Form(
          tuple(
            "address" -> text,
            "memo" -> text,
            "active" -> boolean
          )
        ).bindFromRequest

        if (form.hasErrors)
          return jsonError("Invalid parameter")
        val (addr, memo, active) = form.get

        val original = Address.findByAddress(addr)
        if (original.isEmpty)
          return jsonError("Invalid address")
        if (original.get.user != user.id.get)
          return jsonError("You are not owner")
        if (memo.length >= 1024)
          return jsonError("Memo is too logn")

        val address = Address(
          address = original.get.address,
          user = original.get.user,
          memo = memo,
          active = active
        )

        val result = Address.update(address)

        if (result) {
          Logger.info(s"User ${user.name} updated address ${address.address} (${address.memo}, ${address.active})")
          Ok(JsObject(Seq(
            "success" -> JsBoolean(true)
          )))
        } else {
          Logger.error(s"User ${user.name} failed to update address ${address.address}")
          jsonError("Failed to update address")
        }
      }
      func()
    }, {
      jsonError("Login is needed")
    })
  }

  def addressToJson(address: Address) = {
    val format = new SimpleDateFormat("yyyy/MM/dd HH:mm")
    JsObject(Seq(
      "address" -> JsString(address.address),
      "memo" -> JsString(address.memo),
      "created_at" -> JsString(format.format(address.createdAt.get)),
      "modified_at" -> JsString(format.format(address.modifiedAt.get)),
      "active" -> JsBoolean(address.active)
    ))
  }

  def listAddress = Action { implicit request =>
    checkLogin({ user =>
      val form = Form(
        tuple(
          "filter" -> text,
          "includeInactive" -> boolean,
          "orderBy" -> text,
          "order" -> text,
          "offset" -> number
        )
      ).bindFromRequest

      if (form.hasErrors) {
        jsonError("Invalid parameter")
      } else {
        val num = 100
        val address = Address.findByUser(
          user = user,
          filter = form.get._1,
          includeInactive = form.get._2,
          orderBy = form.get._3,
          order = form.get._4,
          offset = form.get._5,
          limit = num+1)
        val rest = address.length == num+1

        Ok(JsObject(Seq(
          "success" -> JsString("ok"),
          "address" -> JsArray((if (rest) address.init else address).map(addressToJson)),
          "rest" -> JsBoolean(rest)
        )))
      }
    }, {
      jsonError("Login is needed")
    })
  }

  def createRandomString(n: Int, isLower: Boolean = false) = {
    val R = new Random(new SecureRandom())

    if (isLower) {
      val A = "0123456789abcdefghijklmnopqrstuvwxyz"
      (0 until n).map{_ => A(R.nextInt(A.length()))}.mkString
    } else {
      R.alphanumeric.take(n).mkString
    }
  }

  def resetPasswordForm() = Action { implicit request =>
    checkLogin({ user =>
      Ok(views.html.resetpass(Some(user)))
    }, {
      Ok(views.html.resetpass(None))
    })
  }

  def resetPassword(method: String) = Action { implicit request =>
    val redirect = Redirect(routes.Application.resetPasswordForm)

    def reset(user: User, error: String) = {
      val password = createRandomString(16)

      val updated = User(
        id = user.id,
        name = user.name,
        password = User.hashPassword(password),
        resetKey = user.resetKey,
        mailbox = user.mailbox,
        popId = user.popId,
        popPassword = createRandomString(16),
        active = true
      )

      if (User.update(updated)) {
        Logger.info(s"User ${user.name} reset password to ${updated.password}")
        Ok(views.html.resetpassresult(user, password))
      } else {
        Logger.info(s"User ${user.name} failed to reset password to ${updated.password}")
        redirect.flashing(error -> "ログインが必要です")
      }
    }

    method match {
      case "password" =>
        checkLogin({ user =>
          Form("password" -> text).bindFromRequest.fold( error => {
            redirect.flashing("errorPassword" -> "パラメタエラー")
          }, password => {
            if (User.authenticate(user.name, password).isEmpty) {
              redirect.flashing("errorPassword" -> "無効なパスワードです")
            } else {
              reset(user, "errorPassword")
            }
          })
        }, {
          redirect.flashing("errorPassword" -> "ログインが必要です")
        })
      case "resetkey" =>
        Form("resetkey" -> text).bindFromRequest.fold( error => {
          redirect.flashing("errorResetKey" -> "パラメタエラー")
        }, resetKey => {
          val user =User.findByResetKey(resetKey)
          if (user.isEmpty) {
            redirect.flashing("errorResetKey" -> "無効なリセットキーです")
          } else {
            reset(user.get, "errorResetKey")
          }
        })
      case  _ =>
        redirect
    }
  }
}
