package models

import play.api.libs.json._

case class Device(deviceSerialNumber: String,
                  deviceType: String,
                  displayName: String,
                  id: String,
                  sensorLimit: Option[Seq[SensorLimit]])

object Device {
  implicit val deviceReads: Reads[Device] = Json.reads
}
