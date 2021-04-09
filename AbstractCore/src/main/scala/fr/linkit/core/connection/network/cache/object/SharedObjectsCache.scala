/*
 *  Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can only use it for personal uses, studies or documentation.
 *  You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 *  ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 *  Please contact maximebatista18@gmail.com if you need additional information or have any
 *  questions.
 */

package fr.linkit.core.connection.network.cache.`object`

import fr.linkit.api.connection.network.cache.{SharedCacheFactory, SharedCacheManager}
import fr.linkit.api.connection.packet.Packet
import fr.linkit.api.connection.packet.traffic.PacketInjectableContainer
import fr.linkit.core.connection.network.cache.AbstractSharedCache
import fr.linkit.core.connection.packet.fundamental.RefPacket.ObjectPacket
import fr.linkit.core.connection.packet.traffic.ChannelScopes
import fr.linkit.core.connection.packet.traffic.channel.request.{RequestBundle, RequestPacketChannel}

import scala.collection.mutable

class SharedObjectsCache(handler: SharedCacheManager,
                         identifier: Long,
                         channel: RequestPacketChannel) extends AbstractSharedCache[Serializable](handler, identifier, channel) {

    private val localChips       = new mutable.HashMap[Int, ObjectChip[_ <: Serializable]]()
    private val unFlushedPuppets = new mutable.HashSet[(Int, Serializable)]

    def chipObject[S <: Serializable](id: Int, any: S): _ <: S = {
        chipObject(id, any, channel.traffic.supportIdentifier)
    }

    def getChip[S <: Serializable](id: Int): Option[ObjectChip[S]] = {
        localChips.get(id) match {
            case None       => None
            case Some(chip) => chip match {
                case chip: ObjectChip[S] => Some(chip)
                case _                   => None
            }
        }
    }

    def getChippedObject[S <: Serializable](id: Int): Option[S] = {
        getChip[S](id).map(_.puppet)
    }

    override protected def handleBundle(bundle: RequestBundle): Unit = {
        val response = bundle.packet
        val id       = response.getAttribute[Int]("id").get
        val owner    = bundle.coords.senderID

        bundle.packet.nextPacket[Packet] match {
            case ObjectPacket((id: Int, puppet: Serializable)) =>
                if (!localChips.contains(id))
                    chipObject(id, puppet, owner)

            case _ => localChips.get(id).fold() { chip =>
                chip.handleBundle(bundle)
            }
        }
    }

    override protected def setCurrentContent(content: Array[Serializable]): Unit = {
        val contentMap = content.asInstanceOf[Array[(Int, (Serializable, String))]]
                .toMap
        contentMap.foreachEntry((id, pair) => {
            localChips.get(id) match {
                case None       => chipObject(id, pair._1, pair._2)
                case Some(chip) => chip.updatePuppet(pair._1)
            }
        })
    }

    override def currentContent: Array[Any] = localChips
            .map(pair => (pair._1, (pair._2.puppet, pair._2.owner)))
            .toArray

    override var autoFlush: Boolean = true

    override def flush(): this.type = {
        unFlushedPuppets.foreach(pair => flushPuppet(pair._1, pair._2))
        unFlushedPuppets.clear()
        this
    }

    override def modificationCount(): Int = -1

    private def chipObject[S <: Serializable](id: Int, puppet: S, owner: String): _ <: S = {
        val chip = new ObjectChip[S](channel, id, owner, puppet)
        if (autoFlush)
            flushPuppet(id, puppet)
        else unFlushedPuppets += ((id, puppet))

        localChips.put(id, chip)

        //TODO get or generate chip class.
        puppet
    }

    private def flushPuppet(id: Int, puppet: Serializable): Unit = {
        makeRequest(ChannelScopes.broadcast)
                .addPacket(ObjectPacket((id, puppet)))
                .putAttribute("id", id)
                .submit()
    }

}

object SharedObjectsCache extends SharedCacheFactory[SharedObjectsCache] {

    override def createNew(handler: SharedCacheManager,
                           identifier: Long, baseContent: Array[Any],
                           container: PacketInjectableContainer): SharedObjectsCache = {
        val channel = container.getInjectable(5, ChannelScopes.broadcast, RequestPacketChannel)
        new SharedObjectsCache(handler, identifier, channel)
    }

}
