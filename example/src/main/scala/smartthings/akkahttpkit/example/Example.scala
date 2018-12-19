/*
 * Copyright 2018 SmartThings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package smartthings.akkahttpkit.example

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import smartthings.akkahttpkit.example.modules.RouteModule
import smartthings.akkahttpkit.example.config.{ExampleConfig, ServerConfig}
import smartthings.scalakit.modules.Module
import smartthings.scalakit.{ConfigurationParseError, ConfigurationReadError}
import smartthings.akkahttpkit.example.modules.ConfigModule

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import smartthings.scalakit.async.catseffect.Implicits.asyncForIO

class Example extends StrictLogging {

  trait RuntimeModule extends ConfigModule[ExampleConfig] with RouteModule with Module

  def config(args: Array[String]): IO[ExampleConfig] = IO {
    ExampleConfig(args) match {
      case Left(ConfigurationParseError(pe)) => logger.error(pe); sys.exit(1)
      case Left(ConfigurationReadError(re)) => logger.error(re.toList.mkString(", ")); sys.exit(2)
      case Right(c) => c
    }
  }

  def modules(exampleConfig: ExampleConfig): IO[RuntimeModule] = IO {
    new RuntimeModule {
      override def config: ExampleConfig = exampleConfig
    }
  }

  def system(name: String): IO[ActorSystem] = IO(ActorSystem(name))

  def bind(route: Route, config: ServerConfig, system: ActorSystem): IO[Http.ServerBinding] = IO.fromFuture(IO {
    implicit val s: ActorSystem = system
    implicit val mat: Materializer = ActorMaterializer()

    Http().bindAndHandle(
      route,
      config.host,
      config.port
    )
  })

  def registerShutdown(binding: Http.ServerBinding, system: ActorSystem, module: Module): IO[Unit] = IO {
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeClusterShutdown, "example-shutdown") { () =>
      implicit val ec: ExecutionContext = system.dispatcher
      for {
        _ <- binding.terminate(5.millis)
        _ <- binding.whenTerminationSignalIssued
        _ <- module.shutdown.unsafeToFuture()
      } yield Done
    }
  }

}

object Example extends App with StrictLogging {

  val binding = for {
    app               <- IO(new Example())
    config            <- app.config(args)
    modules           <- app.modules(config)
    _                 <- modules.startup
    system            <- app.system("example")
    binding           <- app.bind(modules.route, modules.config.server, system)
    _                 <- app.registerShutdown(binding, system, modules)
  } yield binding

  binding.runAsync {
    case Left(e) => IO(logger.error("Failed to start server", e))
    case Right(b) => IO(logger.info(s"Server started on ${b.localAddress}"))
  }.unsafeRunSync()

}
