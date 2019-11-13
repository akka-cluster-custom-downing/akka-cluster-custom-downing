package org.sisioh.akka.cluster.custom.downing

import akka.cluster.MultiNodeClusterSpec
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

final case class MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet: Boolean) extends MultiNodeConfig {
  val nodeA = role("nodeA")
  val nodeB = role("nodeB")
  val nodeC = role("nodeC")
  val nodeD = role("nodeD")
  val nodeE = role("nodeE")

  commonConfig(
    ConfigFactory
      .parseString("""
      |akka.cluster.downing-provider-class = "org.sisioh.akka.cluster.custom.downing.OldestAutoDowning"
      |custom-downing {
      |  stable-after = 1s
      |
      |  oldest-auto-downing {
      |    oldest-member-role = ""
      |    down-if-alone = true
      |    shutdown-actor-system-on-resolution = false
      |  }
      |}
      |akka.cluster.metrics.enabled=off
      |akka.actor.warn-about-java-serializer-usage = off
      |akka.remote.log-remote-lifecycle-events = off
    """.stripMargin)
      .withFallback(MultiNodeClusterSpec.clusterConfig(failureDetectorPuppet))
  )

  nodeConfig(nodeA, nodeB, nodeC, nodeD, nodeE)(ConfigFactory.parseString("""
      |akka.cluster {
      |  roles = [role]
      |}
    """.stripMargin))

}
