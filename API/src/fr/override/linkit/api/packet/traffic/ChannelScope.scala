/*
 * Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can only use it for personal uses, studies or documentation.
 * You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 * ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 * Please contact maximebatista18@gmail.com if you need additional information or have any
 * questions.
 */

package fr.`override`.linkit.api.packet.traffic

import fr.`override`.linkit.api.exception.ForbiddenIdentifierException
import fr.`override`.linkit.api.packet.Packet

trait ChannelScope {
    val writer: PacketWriter

    def sendToAll(packet: Packet): Unit

    def sendTo(packet: Packet, targetIDs: String*): Unit

    def areAuthorised(identifiers: String*): Boolean

    def copyFromWriter(writer: PacketWriter): ChannelScope

    def canConflictWith(scope: ChannelScope): Boolean

    def assertAuthorised(identifiers: String*): Unit = {
        if (!areAuthorised(identifiers: _*))
            throw new ForbiddenIdentifierException(s"this identifier '${identifiers}' is not authorised by this scope.")
    }

    def equals(obj: Any): Boolean

}

object ChannelScope {

    final case class BroadcastScope private(override val writer: PacketWriter) extends ChannelScope {
        override def sendToAll(packet: Packet): Unit = writer.writeBroadcastPacket(packet)

        override def sendTo(packet: Packet, targetIDs: String*): Unit = {
            writer.writePacket(packet, targetIDs: _*)
        }

        override def areAuthorised(identifiers: String*): Boolean = true //everyone is authorised in a BroadcastScope

        override def canConflictWith(scope: ChannelScope): Boolean = {
            //As Long As everyone is authorised by a BroadcastScope,
            //the other scope wouldn't conflict with this scope only if it discards all identifiers.
            scope.isInstanceOf[BroadcastScope] || scope.canConflictWith(this)
        }

        override def copyFromWriter(writer: PacketWriter): BroadcastScope = BroadcastScope(writer)

        override def equals(obj: Any): Boolean = {
            obj.isInstanceOf[BroadcastScope]
        }
    }

    final case class ReservedScope private(override val writer: PacketWriter, authorisedIds: String*) extends ChannelScope {
        override def sendToAll(packet: Packet): Unit = {
            authorisedIds.foreach(writer.writePacket(packet, _))
        }

        override def sendTo(packet: Packet, targetIDs: String*): Unit = {
            assertAuthorised(targetIDs:_*)
            writer.writePacket(packet, targetIDs:_*)
        }

        override def areAuthorised(identifier: String*): Boolean = {
            authorisedIds.containsSlice(identifier)
        }

        override def copyFromWriter(writer: PacketWriter): ReservedScope = ReservedScope(writer, authorisedIds: _*)

        override def canConflictWith(scope: ChannelScope): Boolean = {
            scope.areAuthorised(authorisedIds: _*)
        }

        override def equals(obj: Any): Boolean = {
            obj match {
                case ReservedScope(authorisedIds) => authorisedIds == this.authorisedIds
                case _ => false
            }
        }
    }

    trait ScopeFactory[S <: ChannelScope] {
        def apply(writer: PacketWriter): S
    }

    def broadcast: ScopeFactory[BroadcastScope] = BroadcastScope(_)

    def reserved(authorised: String*): ScopeFactory[ReservedScope] = ReservedScope(_, authorised: _*)

}