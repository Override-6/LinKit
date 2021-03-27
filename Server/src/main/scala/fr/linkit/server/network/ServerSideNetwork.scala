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

package fr.linkit.server.network

import fr.linkit.api.connection.network.{ExternalConnectionState, NetworkEntity}
import fr.linkit.api.connection.packet.traffic.PacketTraffic
import fr.linkit.core.connection.network.cache.SharedInstance
import fr.linkit.core.connection.network.cache.collection.{BoundedCollection, CollectionModification}
import fr.linkit.core.connection.network.{AbstractNetwork, SelfNetworkEntity}
import fr.linkit.core.connection.packet.traffic.channel.RequestPacketChannel
import fr.linkit.server.connection.{ServerConnection, ServerExternalConnection}

import java.sql.Timestamp

class ServerSideNetwork(serverConnection: ServerConnection)(implicit traffic: PacketTraffic)
        extends AbstractNetwork(serverConnection) {

    override           val connectionEntity: NetworkEntity                              = createServerEntity()
    override protected val entities        : BoundedCollection.Immutable[NetworkEntity] = {
        sharedIdentifiers
                //.addListener((_, _, _) => if (entities != null) println("entities are now : " + entities)) //debug purposes
                .add(serverIdentifier)
                .flush()
                .mapped(createEntity)
                .addListener(handleTraffic)
    }

    override val startUpDate: Timestamp = globalCache.post(2, new Timestamp(System.currentTimeMillis()))

    override def serverIdentifier: String = serverConnection.supportIdentifier

    //The current connection is the network's server connection.
    override def serverEntity: NetworkEntity = connectionEntity

    override def createEntity0(identifier: String, communicator: RequestPacketChannel): NetworkEntity = {
        val entityCache = newCacheManager(identifier, identifier)
        val v           = new ExternalConnectionNetworkEntity(serverConnection, identifier, entityCache)
        v
    }

    def removeEntity(identifier: String): Unit = {
        getEntity(identifier)
                .foreach(entity => {
                    if (entity.getConnectionState != ExternalConnectionState.CLOSED)
                        throw new IllegalStateException(s"Could not remove entity '$identifier' from network as long as it still open")
                    sharedIdentifiers.remove(identifier)
                })
    }

    def addEntity(connection: ServerExternalConnection): Unit = {
        if (serverConnection ne connection.session.server)
            throw new IllegalAccessException("Attempted to add connection into a network that does not belongs to it")

        val identifier = connection.boundIdentifier
        if (!sharedIdentifiers.contains(identifier))
            sharedIdentifiers.add(identifier)
    }

    def createServerEntity(): NetworkEntity = {
        val selfCache    = newCacheManager(serverIdentifier, serverConnection)
        val serverEntity = new SelfNetworkEntity(serverConnection, ExternalConnectionState.CONNECTED, selfCache) //Server always connected to himself
        serverEntity
                .entityCache
                .get(3, SharedInstance[ExternalConnectionState])
                .set(ExternalConnectionState.CONNECTED) //technically always connected
        serverEntity
    }

    private def handleTraffic(mod: CollectionModification, index: Int, entityOpt: Option[NetworkEntity]): Unit = {
        /*lazy val entity = entityOpt.orNull //get
        println(s"mod = ${mod}")
        println(s"index = ${index}")
        println(s"entity = ${entity}")

        import CollectionModification._
        val event = mod match {
            case ADD => NetworkEvents.entityAdded(entity)
            case REMOVE => NetworkEvents.entityRemoved(entity)
            case _ => return
        }
        //server.eventNotifier.notifyEvent(server.networkHooks, event)
        */
    }
}