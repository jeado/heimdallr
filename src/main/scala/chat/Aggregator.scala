/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package chat

import akka.actor._
import chat.EventConstants._
import scala.concurrent.ExecutionContext
import scala.collection.mutable

class Aggregator extends Actor with ActorLogging {
  implicit val system = context.system
  implicit val executionContext: ExecutionContext = context.dispatcher

  // Counter of number of users for each chat room.
  private var roomStats: mutable.Map[Int, Int] = mutable.Map.empty[Int, Int]  //<- guest + member
  private var guestCount: mutable.Map[Int, Int] = mutable.Map.empty[Int, Int]
  private var memberCount: mutable.Map[Int, Int] = mutable.Map.empty[Int, Int]

  override def preStart(): Unit = {
    log.info( "Heimdallr Aggregator Staring ..." )
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info( "Heimdallr Aggregator Restarting ..." )
    preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info( "Heimdallr Aggregator Restarted." )
    log.info( reason.toString )
  }

  override def postStop(): Unit = {
    log.info( "Heimdallr Aggregator Down !" )
  }

  def removeChatRoom(chatRoomID: Int): Unit = {
    memberCount.remove(chatRoomID)
    guestCount.remove(chatRoomID)
    roomStats.remove(chatRoomID)
    log.info(s"=> Removed Chat Room($chatRoomID)")
  }

  def updateChatRoom(chatRoomID: Int, users: Int, member: Int, guest: Int): Unit = {
    roomStats += chatRoomID -> users
    if (member > -1) memberCount += chatRoomID -> member
    if (guest > -1) guestCount += chatRoomID -> guest

    /* - debug mode
    val tt = roomStats.values.sum
    val mc= memberCount.values.sum
    val gc = guestCount.values.sum
    log.info(s"=> Updated Chat Room($chatRoomID) : T=$users / M=$member / G=$guest")
    log.info(s"=> Updated Chat Room(TOTAL) : T-$tt / M-$mc / G-$gc")
    */
  }

  override def receive: Receive = {
    case AggregatorView =>
      val total = roomStats.values.sum
      val member= memberCount.values.sum
      val guest = guestCount.values.sum
      log.info(s"=> Updated Chat AggregatorView : T[$total] / M[$member] / G[$guest]")

    case AggregatorCollectionToStats =>
      sender ! ChatRooms.setCollectionValues(roomStats, memberCount, guestCount)

    case AggregatorValueToStats =>
      val total = roomStats.values.sum
      val member= memberCount.values.sum
      val guest = guestCount.values.sum
      sender ! ChatRooms.setTotalCountValues(total, member, guest)

    case AggregatorRoomValueToStats(chatRoomID) =>
      val total = roomStats.getOrElse(chatRoomID, 0)
      val member = memberCount.getOrElse(chatRoomID, 0)
      val guest = guestCount.getOrElse(chatRoomID, 0)
      sender ! ChatRooms.setOnceTotalCountValues(total, member, guest)

    case UpdateChatCount(chatRoomID, users, member, guest) =>
      updateChatRoom(chatRoomID, users, member, guest)

    case RemoveChatRoom(chatRoomID) =>
      removeChatRoom(chatRoomID)

    case x =>
      log.warning("Heimdallr Aggregator Unknown message : " + x)
  }
}
