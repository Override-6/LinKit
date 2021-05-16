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

package fr.linkit.engine.connection.cache.repo

import fr.linkit.api.connection.cache.repo.PuppetDescription.MethodDescription
import fr.linkit.api.connection.cache.repo._
import fr.linkit.api.connection.cache.repo.generation.PuppeteerDescription
import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.channel.ChannelScope.ScopeFactory
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.connection.cache.repo.tree.PuppetNode
import fr.linkit.engine.connection.packet.fundamental.RefPacket
import fr.linkit.engine.connection.packet.fundamental.RefPacket.ObjectPacket
import fr.linkit.engine.connection.packet.traffic.ChannelScopes
import fr.linkit.engine.connection.packet.traffic.channel.request.RequestPacketChannel

import java.util.concurrent.ThreadLocalRandom
import scala.reflect.ClassTag

class SimplePuppeteer[S](channel: RequestPacketChannel,
                         override val repo: ObjectRepository[_],
                         override val puppeteerDescription: PuppeteerDescription,
                         val puppetDescription: PuppetDescription[S]) extends Puppeteer[S] {

    private val bcScope                                = prepareScope(ChannelScopes.discardCurrent)
    private var puppet       : S                       = _
    private var puppetWrapper: S with PuppetWrapper[S] = _

    override val ownerID: String = puppeteerDescription.owner

    override def getPuppet: S = puppet

    override def getPuppetWrapper: S with PuppetWrapper[S] = puppetWrapper

    override def sendInvokeAndWaitResult[R](methodId: Int, args: Array[Any]): R = {
        val desc = puppetDescription.getMethodDesc(methodId).getOrElse {
            throw new NoSuchMethodException(s"Remote method not found for id '$methodId'")
        }

        AppLogger.debug(s"Remotely invoking method $methodId(${args.mkString(",")})")
        val result = channel.makeRequest(bcScope)
                .addPacket(ObjectPacket((methodId, synchronizedArgs(desc, args))))
                .submit()
                .nextResponse
                .nextPacket[RefPacket[R]].value
        result match {
            //FIXME ambiguity with broadcast method invocation.
            case ThrowableWrapper(e) => throw new RemoteInvocationFailedException(s"Invocation of method $methodId with arguments '${args.mkString(", ")}' failed.", e)
            case result              => result
        }
    }

    override def sendInvoke(methodId: Int, args: Array[Any]): Unit = {
        val desc = puppetDescription.getMethodDesc(methodId).getOrElse {
            throw new NoSuchMethodException(s"Remote method not found for id '$methodId'")
        }
        AppLogger.debug(s"Remotely invoking method ${desc.method.getName}(${args.mkString(",")})")

        if (desc.isHidden)
            channel.makeRequest(bcScope)
                    .addPacket(InvocationPacket(puppeteerDescription.treeViewPath, methodId, args))
                    .submit()
                    .detach()
    }

    override def sendFieldUpdate(fieldId: Int, newValue: Any): Unit = {
        AppLogger.vDebug(s"Remotely associating field '${
            puppetDescription.getFieldDesc(fieldId).get.field.getName
        }' to value $newValue.")
        val desc  = puppetDescription.getFieldDesc(fieldId).getOrElse {
            throw new NoSuchMethodException(s"Remote field not found for id '$fieldId'")
        }
        val value = if (desc.isSynchronized) synchronizedObj(newValue) else newValue
        channel.makeRequest(bcScope)
                .addPacket(InvocationPacket(puppeteerDescription.treeViewPath, fieldId, Array(newValue)))
                .submit()
                .detach()
    }

    override def sendPuppetUpdate(newVersion: S): Unit = {
        //TODO optimize, directly send the newVersion object to copy paste instead of all its fields.
        puppetDescription.listFields()
                .foreach(fieldDesc => if (!fieldDesc.isHidden) {
                    sendFieldUpdate(fieldDesc.fieldID, fieldDesc.field.get(newVersion))
                })
    }

    override def init(wrapper: S with PuppetWrapper[S], puppet: S): Unit = {
        if (this.puppet != null || this.puppetWrapper != null) {
            throw new IllegalStateException("This Puppeteer already controls a puppet instance !")
        }
        this.puppetWrapper = wrapper
        this.puppet = puppet
    }

    private def synchronizedArgs(desc: MethodDescription, args: Array[Any]): Array[Any] = {
        desc.synchronizedParams
                .zip(args)
                .map(pair => if (pair._1) synchronizedObj(pair._2) else pair._2)
                .toArray
    }

    private def synchronizedObj(obj: Any): Any = {
        val id           = ThreadLocalRandom.current().nextInt()
        val currentPath  = puppeteerDescription.treeViewPath
        val objPath      = currentPath ++ Array(id)
        val descriptions = repo.descriptions
        val isIntended   = channel.traffic.supportIdentifier == ownerID
        repo.genSynchronizedObject(objPath, obj, ownerID, descriptions) {
            (wrapper, childPath) =>
                val id          = childPath.last
                val description = repo.getPuppetDescription(ClassTag(wrapper.getClass))
                repo.center.getPuppet(currentPath).get.getGrandChild(childPath.drop(currentPath.length).dropRight(1))
                        .fold(throw new NoSuchPuppetException(s"Puppet Node not found in path ${childPath.mkString("$", " -> ", "")}")) {
                            parent =>
                                val chip      = ObjectChip[Any](ownerID, description, wrapper)
                                val puppeteer = wrapper.getPuppeteer.asInstanceOf[Puppeteer[Any]]
                                parent.addChild(id, new PuppetNode(puppeteer, chip, descriptions, isIntended, id, _))
                        }
        }
    }

    private def prepareScope(factory: ScopeFactory[_ <: ChannelScope]): ChannelScope = {
        if (channel == null)
            return null
        val writer = channel.traffic.newWriter(channel.identifier)
        val scope  = factory.apply(writer)
        repo.drainAllDefaultAttributes(scope)
        scope
    }

}
