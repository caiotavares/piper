import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
  lazy val finagleNetty4 = "com.twitter" %% "finagle-netty4" % "19.12.0"
}
