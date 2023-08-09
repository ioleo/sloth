Global / onChangedBuildSource := IgnoreSourceChanges

inThisBuild(Seq(
  organization := "com.github.cornerman",

  crossScalaVersions := Seq("2.13.11", "3.3.0"),
  scalaVersion := crossScalaVersions.value.tail.head,

  licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),

  homepage := Some(url("https://github.com/cornerman/sloth")),

  scmInfo := Some(ScmInfo(
    url("https://github.com/cornerman/sloth"),
    "scm:git:git@github.com:cornerman/sloth.git",
    Some("scm:git:git@github.com:cornerman/sloth.git"))
  ),

  pomExtra :=
    <developers>
      <developer>
        <id>jkaroff</id>
        <name>Johannes Karoff</name>
        <url>https://github.com/cornerman</url>
      </developer>
    </developers>
))

lazy val commonSettings = Seq(
  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq.empty
    case _ => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
  }),
)

lazy val jsSettings = Seq(
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq("-scalajs")
    case _ => Seq.empty
  }),
)

enablePlugins(ScalaJSPlugin)

lazy val types = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Seq("2.12.18", "2.13.11", "3.3.0"),
    name := "sloth-types",
    libraryDependencies ++=
      Deps.cats.value ::
      Nil
  ).jsSettings(jsSettings)

lazy val sloth = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(types)
  .settings(commonSettings)
  .settings(
    name := "sloth",
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq.empty
      case _ => Seq(Deps.scalaReflect.value % scalaVersion.value % Provided)
    }),
    libraryDependencies ++=
      Deps.cats.value ::
      Deps.chameleon.value ::

      Deps.circe.core.value % Test ::
      Deps.circe.generic.value % Test ::
      Deps.circe.parser.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  ).jsSettings(jsSettings)

lazy val examples = project
  .in(file("examples"))
  .settings(
    scalaVersion := "3.3.0",
    libraryDependencies ++= Seq(
      //"com.github.cornerman" %%% "sloth" % "0.7.0",
      "com.github.cornerman" %%% "chameleon" % "0.3.7",
      "dev.zio" %%% "zio" % "2.0.15",
      "dev.zio" %%% "zio-json" % "0.6.0"
    ),
    scalacOptions ++= Seq("-scalajs")
  ).enablePlugins(ScalaJSPlugin)
  .dependsOn(sloth.js)