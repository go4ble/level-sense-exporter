package models

import play.api.libs.json._

case class DeviceList(deviceList: Seq[Device], success: Boolean)

object DeviceList {
  implicit val deviceListReads: Reads[DeviceList] = Json.reads
}
