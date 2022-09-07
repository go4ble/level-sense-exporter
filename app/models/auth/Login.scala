package models.auth

import play.api.libs.json._

case class Login(email: String, password: String)

object Login {
  implicit val loginWrites: OWrites[Login] = Json.writes
}
