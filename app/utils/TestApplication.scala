package utils

import play.api.{Application, ApplicationLoader, Environment, Mode}

import scala.annotation.unused

@unused
object TestApplication {
  lazy private val environment = Environment(new java.io.File("."), this.getClass.getClassLoader, Mode.Dev)
  lazy private val context = ApplicationLoader.Context.create(environment)
  lazy private val loader = ApplicationLoader(context)

  lazy val app: Application = loader.load(context)
}
