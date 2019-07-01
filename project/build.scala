import java.io.File

import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.Keys.{javaOptions, _}
import sbt.Resolver
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

object CustomDockerImage {
  val dockerfile = Seq(
    ExecCmd("RUN", "apt-get", "update", "-y"),
    ExecCmd("RUN", "apt-get", "install", "-y", "curl"),
    ExecCmd(
      "RUN",
      "curl",
      "-sLk",
      "https://deb.nodesource.com/setup_6.x",
      "-o",
      "nodesource_setup.sh"),
    ExecCmd("RUN", "chmod", "u+x", "nodesource_setup.sh"),
    ExecCmd("RUN", "./nodesource_setup.sh"),
    ExecCmd(
      "RUN",
      "apt-get",
      "install",
      "-y",
      "nodejs",
      "build-essential",
      "git",
      "openssh-client"),
    ExecCmd("RUN", "npm", "install", "-g", "mocha@3.2.0"),
    ExecCmd(
      "RUN",
      "bash",
      "-c",
      "curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add -"),
    ExecCmd(
      "RUN",
      "bash",
      "-c",
      "echo 'deb https://dl.yarnpkg.com/debian/ stable main' | tee /etc/apt/sources.list.d/yarn.list"),
    ExecCmd("RUN", "bash", "-c", "apt-get update && apt-get install yarn"),
    ExecCmd("RUN", "apt-get", "clean"),
    ExecCmd("RUN", "mkdir", "-p", "/home/daemon"),
    ExecCmd("RUN", "mkdir", "-p", "/home/daemon/.ssh"),
    ExecCmd(
      "RUN",
      "bash",
      "-c",
      "echo -e 'eval $(ssh-agent -s)' >> /home/daemon/.bashrc"),
    ExecCmd(
      "RUN",
      "bash",
      "-c",
      "echo 'git config --global user.email name@example.com' >> /home/daemon/.bashrc"),
    ExecCmd(
      "RUN",
      "bash",
      "-c",
      "echo 'git config --global user.name Name' >> /home/daemon/.bashrc"),
    //ExecCmd("RUN", "bash", "-c", "echo -e 'trap kill $SSH_AGENT_PID EXIT >> /home/daemon/.bashrc"),
    ExecCmd(
      "RUN",
      "bash",
      "-c",
      "echo -e 'Host *\\n\\tStrictHostKeyChecking no\\n\\n' > /home/daemon/.ssh/config"),
    ExecCmd("RUN", "bash", "-c", "chmod 400 /home/daemon/.ssh/*"),
    ExecCmd("RUN", "chown", "-R", "daemon:daemon", "/home/daemon"),
    ExecCmd("RUN", "usermod", "-d", "/home/daemon", "daemon"))
}

object Settings {
  lazy val common = Seq(
    // We want the JVM to be forked for runs
    fork := true,
    traceLevel in run := 0,
    baseDirectory in run := new File("."),
    organization := "com.github.algobardo",
    version := "latest",
    scalaVersion := "2.11.12",
    traceLevel in run := 0,
    scalacOptions := Seq(
      "-deprecation",
      "-encoding",
      "UTF-8", // yes, this is 2 args
      "-feature",
      //"-language:existentials",
      //"-language:higherKinds",
      //"-language:implicitConversions",
      "-unchecked",
      //"-Xfatal-warnings",
      //"-Xlint",
      //"-Yno-adapted-args",
      //"-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
      "-Ywarn-numeric-widen",
      //"-Ywarn-value-discard",
      "-Xfuture",
      "-Xexperimental", // SAM type supports for Java8
      "-Ywarn-unused-import", // 2.11 only
      "-optimize"),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    javaOptions ++= Seq("-server", "-Xmx13G", "-Xss515m", "-XX:+UseG1GC"))
}

object ServerDocker {
  val settings = Seq(
    dockerBaseImage := "airdock/oracle-jdk:jdk-1.8",
    dockerExposedPorts := Seq(8088),
    dockerRepository := Some("localhost:5000"),
    packageName in Docker := name.value + "-docker",
    dockerUpdateLatest := true,
    //version in Docker := "latest",
    defaultLinuxInstallLocation in Docker := s"/opt/${name.value}")
  //packageName in Docker := packageName.value
  //version in Docker := version.value
  dockerExposedPorts := Seq(9000, 9443)
  //dockerExposedVolumes := Seq("/opt/docker/logs")
}
