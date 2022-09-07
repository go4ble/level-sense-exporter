package models

import play.api.libs.json._

case class AlarmConfig(device: Device, success: Boolean)

object AlarmConfig {
  implicit val alarmConfigReads: Reads[AlarmConfig] = Json.reads
}
