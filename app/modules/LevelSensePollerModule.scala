package modules

import akka.actor.ActorSystem
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.{Counter, Gauge, Summary}
import models.auth.{Login, SessionKey}
import models.{AlarmConfig, Device, DeviceList, SensorLimit}
import play.api.cache.AsyncCacheApi
import play.api.http.Status
import play.api.inject.{SimpleModule, bind}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logging}

import javax.inject.{Inject, Singleton}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class LevelSensePollerModule extends SimpleModule(bind[LevelSensePollerModule.LevelSensePoller].toSelf.eagerly())

object LevelSensePollerModule {
  @Singleton
  private class LevelSensePoller @Inject() (actorSystem: ActorSystem, config: Configuration, wsClient: WSClient, cache: AsyncCacheApi)(implicit
      ec: ExecutionContext
  ) extends Logging {
    private val InitialDelay = 5.seconds
    private val Timeout = 18.seconds
    private val PollingPeriod = config.get[Int]("app.pollingPeriodMinutes").minutes
    private val Username = config.get[String]("app.username").ensuring(!_.isBlank, "username must be set")
    private val Password = config.get[String]("app.password").ensuring(!_.isBlank, "password must be set")
    private val IncludeHotspotMetrics = config.get[Boolean]("app.includeHotspotMetrics")
    private val LevelSenseBaseUrl = "https://dash.level-sense.com/Level-Sense-API/web/api"
    private val AuthCacheKey = "modules.LevelSensePollerModule.LevelSensePoller.AuthCacheKey"
    private val DeviceListCacheKey = "modules.LevelSensePollerModule.LevelSensePoller.DeviceListCacheKey"
    private val CacheExpiration = 23.hours
    private val SessionKeyHeaderName = "SessionKey"
    private val MetricNamePrefix = "lse_"

    private val LabelNameRequest = "request_name"
    private val LabelValueGetSessionKey = "get_session_key"
    private val LabelValueGetDeviceList = "get_device_list"
    private val LabelValueGetAlarmConfig = "get_alarm_config"

    private val collectors = mutable.HashMap[String, Gauge]()
    private lazy val pollCount = Counter.build(MetricNamePrefix + "poll_count", "Poll Count").register()
    private lazy val lastPolledAt = Gauge.build(MetricNamePrefix + "last_polled_at", "Last Polled At").register()
    private lazy val requestCount = Counter.build(MetricNamePrefix + "request_count", "Request Count").labelNames(LabelNameRequest).register()
    private lazy val requestDuration = Summary.build(MetricNamePrefix + "request_duration", "Request Duration").labelNames(LabelNameRequest).register()

    logger.info(s"Starting LevelSensePoller at interval of $PollingPeriod")

    if (IncludeHotspotMetrics) DefaultExports.initialize()

    actorSystem.scheduler.scheduleAtFixedRate(InitialDelay, PollingPeriod)(() =>
      Await.result(
        for {
          sessionKey <- getSessionKey
          DeviceList(deviceList, _) <- getDeviceList(sessionKey)
          alarmConfigFutures = deviceList.map(device => getAlarmConfig(sessionKey, device.id))
          alarmConfigs <- Future.sequence(alarmConfigFutures)
          devices = alarmConfigs.map(_.device)
        } yield collectDevices(devices),
        Timeout
      )
    )

    private def getSessionKey: Future[SessionKey] = cache.getOrElseUpdate(AuthCacheKey, CacheExpiration) {
      val requestTimer = requestDuration.labels(LabelValueGetSessionKey).startTimer()
      wsClient
        .url(LevelSenseBaseUrl + "/v1/login")
        .withRequestTimeout(Timeout / 3)
        .post(Json.toJson(Login(Username, Password)))
        .map { response =>
          assert(response.status == Status.OK)
          requestCount.labels(LabelValueGetSessionKey).inc()
          requestTimer.observeDuration()
          response.json.as[SessionKey].ensuring(_.success)
        }
    }

    private def getDeviceList(sessionKey: SessionKey): Future[DeviceList] = cache.getOrElseUpdate(DeviceListCacheKey, CacheExpiration) {
      val requestTimer = requestDuration.labels(LabelValueGetDeviceList).startTimer()
      wsClient
        .url(LevelSenseBaseUrl + "/v1/getDeviceList")
        .withRequestTimeout(Timeout / 3)
        .addHttpHeaders(SessionKeyHeaderName -> sessionKey.sessionKey)
        .get()
        .map { response =>
          assert(response.status == Status.OK)
          requestCount.labels(LabelValueGetDeviceList).inc()
          requestTimer.observeDuration()
          response.json.as[DeviceList].ensuring(_.success)
        }
    }

    private def getAlarmConfig(sessionKey: SessionKey, deviceId: String): Future[AlarmConfig] = {
      val requestTimer = requestDuration.labels(LabelValueGetAlarmConfig).startTimer()
      wsClient
        .url(LevelSenseBaseUrl + "/v2/getAlarmConfig")
        .withRequestTimeout(Timeout / 3)
        .addHttpHeaders(SessionKeyHeaderName -> sessionKey.sessionKey)
        .post(Json.obj("id" -> deviceId))
        .map { response =>
          assert(response.status == Status.OK)
          requestCount.labels(LabelValueGetAlarmConfig).inc()
          requestTimer.observeDuration()
          response.json
            .as[AlarmConfig]
            .ensuring(_.success)
            .ensuring(_.device.sensorLimit.isDefined)
        }
    }

    private def collectDevices(devices: Seq[Device]): Unit = {
      devices.foreach { device =>
        device.sensorLimit.get.foreach {
          case sensor @ SensorLimit(currentValue, sensorId, isAlarm, _, sensorDisplayName, sensorDisplayUnits, _, sensorSlug, _) =>
            val includeUnits = if (sensorDisplayUnits.isBlank) "" else "_" + sensorDisplayUnits.toLowerCase
            val valueCollector = collectors
              .getOrElseUpdate(
                sensorId + "_value" + includeUnits,
                Gauge.build(MetricNamePrefix + sensorSlug, sensorDisplayName).labelNames("device_id").register()
              )
              .labels(device.id)
            currentValue.fold(
              currentValueBigDecimal => valueCollector.set(currentValueBigDecimal.doubleValue),
              _.toLowerCase match {
                case DoubleValue(v)        => valueCollector.set(v)
                case "open" | "ok" | "..." => valueCollector.set(0)
                case "close" | "fault"     => valueCollector.set(1)
                case _                     => logger.error(s"Unable to parse sensor value: $sensor")
              }
            )
            val isAlarmCollector = collectors
              .getOrElseUpdate(
                sensorId + "_is_alarm",
                Gauge.build(MetricNamePrefix + sensorSlug + "_is_alarm", sensorDisplayName + " is alarm").labelNames("device_id").register()
              )
              .labels(device.id)
            isAlarmCollector.set(if (isAlarm) 1 else 0)
        }
      }
      pollCount.inc()
      lastPolledAt.setToCurrentTime()
    }
  }

  object DoubleValue {
    def unapply(value: String): Option[Double] = value.toDoubleOption
  }
}
