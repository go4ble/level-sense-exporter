package controllers

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import play.api.mvc._

import java.io.StringWriter
import javax.inject._
import scala.jdk.CollectionConverters._

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
class HomeController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {

  /** Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method will be called when the application receives a `GET` request with a path of `/`.
    */
  def index(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def metrics(names: Seq[String] = Nil): Action[AnyContent] = Action {
    val writer = new StringWriter()
    TextFormat.write004(writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names.toSet.asJava))
    Ok(writer.toString).as(TextFormat.CONTENT_TYPE_004)
  }
}
