package models

import play.api.libs.json._

case class SensorLimit(
    currentValue: Either[BigDecimal, String],
    id: String,
    isAlarm: Boolean,
    lcl: Either[BigDecimal, String],
    sensorDisplayName: String,
    sensorDisplayUnits: String,
    sensorId: String,
    sensorSlug: String,
    ucl: Either[BigDecimal, String]
)

object SensorLimit {
  implicit val sensorLimitReads: Reads[SensorLimit] = Json.reads

  implicit def eitherReads[T, U](implicit tReads: Reads[T], uReads: Reads[U]): Reads[Either[T, U]] = jsValue =>
    jsValue.validate[T].map(Left(_)) orElse jsValue.validate[U].map(Right(_))
}
