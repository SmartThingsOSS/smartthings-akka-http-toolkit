lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

lazy val common = Seq(
  organization := "com.smartthings.akkakit",
  git.baseVersion := "1.0",
  scalaVersion := "2.12.6",

  compileScalastyle := scalastyle.in(Compile).toTask("").value,

  resolvers += Resolver.bintrayRepo("otherUser", "maven"),

  scalacOptions ++= Seq(
    "-deprecation",
    "-Ypartial-unification"
  ),

  javacOptions ++= Seq("-source", "8", "-target", "8", "-Xlint"),
  
  // run style and header checks on test task
  Test / test := (((Test / test) dependsOn compileScalastyle) dependsOn Compile / headerCheck).value,

  libraryDependencies ++= Seq(
    // Testing
    "org.scalatest" %% "scalatest"                  % "3.0.5" % Test,
  ),

  // skip test in assembly phase
  test in assembly := {},

  initialize := {
    if (sys.props("java.specification.version").toDouble < 1.8)
      sys.error("Java 1.8 or newer is required for this project.")
  },
)

lazy val publishSettings =
  Seq(
    homepage := Some(url("https://github.com/smartthingsoss/smartthings-akka-http-toolkit")),
    scmInfo := Some(ScmInfo(url("https://github.com/smartthingsoss/smartthings-akka-http-toolkit"),
      "git@github.com:smartthingsoss/smartthings-akka-http-toolkit.git")),
    developers += Developer("llinder",
      "Lance Linder",
      "lance@smartthings.com",
      url("https://github.com/llinder")),
    organizationName := "SmartThings",
    startYear := Some(2018),
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    pomIncludeRepository := (_ => false),
    publishMavenStyle := true,
    bintrayOrganization := Some("smartthingsoss"),
    bintrayPackage := "smartthings-akka-http-toolkit"
  )

lazy val settings = common ++ publishSettings

lazy val root = project.in(file("."))
  .enablePlugins(GitBranchPrompt)
  .settings(settings)
  .settings(
    name := "akka-http-toolkit-root",
    publishArtifact := false,
    bintrayRelease := false,
    Compile / unmanagedSourceDirectories := Seq.empty,
    Test / unmanagedSourceDirectories    := Seq.empty,
  )
  .aggregate(`example`, `server`)


lazy val server = project.in(file("server"))
  .settings(settings)
  .settings(
    name := "akka-http-toolkit-server",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"              % "10.1.6" ,
    ),
  )

lazy val example = project.in(file("example"))
  .settings(settings)
  .dependsOn(server)
  .settings(
    name := "akka-http-toolkit-example",

    libraryDependencies ++= Seq(
      // AKKA HTTP
      "com.typesafe.akka" %% "akka-http"              % "10.1.6" ,
      "com.typesafe.akka" %% "akka-stream"            % "2.5.19",

      // Scala Toolkit
      "com.smartthings.scalakit" %% "scala-toolkit-core"    % "0.0.1",
      "com.smartthings.scalakit" %% "scala-toolkit-config"  % "0.0.1",
      "com.smartthings.scalakit" %% "scala-toolkit-modules" % "0.0.1",

      // JSON
      "de.heikoseeberger" %% "akka-http-circe"        % "1.22.0",
      "io.circe" %% "circe-core"                      % "0.10.1",
      "io.circe" %% "circe-generic"                   % "0.10.1",
      "io.circe" %% "circe-parser"                    % "0.10.1",
      "io.circe" %% "circe-java8"                     % "0.10.1",

      // Dependency Injection
      "com.softwaremill.macwire" %% "macros"          % "2.3.1",
      "com.softwaremill.common" %% "tagging"          % "2.2.1",

      // Logging
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "io.projectreactor.addons" % "reactor-logback"  % "3.1.6.RELEASE",

      // Gatling Integration Tests
//      "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.0.0" % "it",
//      "io.gatling"            % "gatling-test-framework"    % "3.0.0" % "it",

    ),

    assemblyMergeStrategy in assembly := {
      case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },

    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/opt/${artifact.name}"

      new Dockerfile {
        from("openjdk:11-jre-slim")
        add(artifact, artifactTargetPath)
        env("JAVA_OPTS", "")
        entryPointShell("java", "${JAVA_OPTS}", "-jar", artifactTargetPath)
      }
    },

    imageNames in docker := Seq(
      ImageName(
        namespace = Some("smartthingsoss"),
        repository = "akka-http-toolkit-example",
        tag = if (version.value.contains("SNAPSHOT")) Some("latest") else Some("v" + version.value)
      ),
    )
  )

