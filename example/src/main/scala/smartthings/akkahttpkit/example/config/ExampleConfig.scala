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

package smartthings.akkahttpkit.example.config

import java.io.File

import scopt.OptionParser
import pureconfig.generic.auto._
import smartthings.scalakit.{Configuration, ConfigurationError, ConfigurationReadError}


object ExampleConfig {

  private lazy val parser = new OptionParser[ExampleConfig]("example") {
    // TODO the version should come from an external source
    head("example", "0.1.x")
    // This is strictly a noop so the pre parser external config values shows up in help output
    opt[File]('e', "external-config").action { (_, c) => c}
  }

  def apply(args: Array[String]): Either[ConfigurationError, ExampleConfig] =
    Configuration(parser, args) { typesafeConfig =>
      pureconfig.loadConfig[ExampleConfig](typesafeConfig, "example").left.map(ConfigurationReadError)
    }

}


case class ExampleConfig(server: ServerConfig)

case class ServerConfig(host: String, port: Int)
