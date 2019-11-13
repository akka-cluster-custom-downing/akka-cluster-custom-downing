package org.sisioh.akka.cluster.custom.downing

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.cluster.{ Member, MemberStatus }
import akka.event.Logging

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.FiniteDuration

abstract class MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter)
    with SplitBrainResolver {

  private val log = Logging(context.system, this)

  private var leader                                        = false
  private var roleLeader: Map[String, Boolean]              = Map.empty
  private var membersByAddress: immutable.SortedSet[Member] = immutable.SortedSet.empty(Member.ordering)

  def receiveEvent = {
    case LeaderChanged(leaderOption) =>
      leader = leaderOption.contains(selfAddress)
      if (isLeader) {
        log.info("This node is the new Leader")
      }
      onLeaderChanged(leaderOption)
    case RoleLeaderChanged(role, leaderOption) =>
      roleLeader = roleLeader + (role -> leaderOption.contains(selfAddress))
      if (isRoleLeaderOf(role)) {
        log.info("This node is the new role leader for role {}", role)
      }
      onRoleLeaderChanged(role, leaderOption)
    case MemberUp(m) =>
      log.info("{} is up", m)
      replaceMember(m)
    case UnreachableMember(m) =>
      log.info("{} is unreachable", m)
      replaceMember(m)
      unreachableMember(m)
    case ReachableMember(m) =>
      log.info("{} is reachable", m)
      replaceMember(m)
      remove(m)
    case MemberLeft(m) =>
      log.info("{} left the cluster", m)
      replaceMember(m)
    case MemberExited(m) =>
      log.info("{} exited the cluster", m)
      replaceMember(m)
    case MemberRemoved(m, prev) =>
      log.info("{} was removed from the cluster", m)
      remove(m)
      removeMember(m)
      onMemberRemoved(m, prev)
  }

  def isLeader: Boolean = leader

  def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  def onLeaderChanged(leader: Option[Address]): Unit = {}

  def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {}

  def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {}

  override def initialize(state: CurrentClusterState): Unit = {
    leader = state.leader.exists(_ == selfAddress)
    roleLeader = state.roleLeaderMap.mapValues(_.exists(_ == selfAddress)).toMap
    membersByAddress = immutable.SortedSet.empty(Member.ordering) union state.members.filterNot { m =>
        m.status == MemberStatus.Removed
      }
    super.initialize(state)
  }

  def replaceMember(member: Member): Unit = {
    membersByAddress -= member
    membersByAddress += member
  }

  def removeMember(member: Member): Unit = {
    membersByAddress -= member
  }

  def isLeaderOf(majorityRole: Option[String]): Boolean = majorityRole.fold(isLeader)(isRoleLeaderOf)

  def majorityMemberOf(role: Option[String]): SortedSet[Member] = {
    val ms = membersByAddress
    role.fold(ms)(r => ms.filter(_.hasRole(r)))
  }

  def isMajority(role: Option[String]): Boolean = {
    val ms        = majorityMemberOf(role)
    val okMembers = ms filter isOK
    val koMembers = ms -- okMembers

    val isEqual = okMembers.size == koMembers.size
    return (okMembers.size > koMembers.size ||
    isEqual && ms.headOption.map(okMembers.contains(_)).getOrElse(true))
  }

  def isMajorityAfterDown(members: Set[Member], role: Option[String]): Boolean = {
    val minus =
      if (role.isEmpty) members
      else {
        val r = role.get
        members.filter(_.hasRole(r))
      }
    val ms        = majorityMemberOf(role)
    val okMembers = (ms filter isOK) -- minus
    val koMembers = ms -- okMembers

    val isEqual = okMembers.size == koMembers.size
    return (okMembers.size > koMembers.size ||
    isEqual && ms.headOption.map(okMembers.contains(_)).getOrElse(true))
  }

  private def isOK(member: Member) = {
    (member.status == MemberStatus.Up || member.status == MemberStatus.Leaving) &&
    (!pendingUnreachableMembers.contains(member) && !unstableUnreachableMembers.contains(member))
  }

  private def isKO(member: Member): Boolean = !isOK(member)
}
