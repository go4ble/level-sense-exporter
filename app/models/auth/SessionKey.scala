package models.auth

import play.api.libs.json._

case class SessionKey(sessionKey: String, success: Boolean)

object SessionKey {
  implicit val sessionKeyReads: Reads[SessionKey] = Json.reads
}
