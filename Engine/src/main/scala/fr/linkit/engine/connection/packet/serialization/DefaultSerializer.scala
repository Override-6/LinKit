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

package fr.linkit.engine.connection.packet.serialization

import fr.linkit.api.connection.network.Network
import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.connection.packet.serialization.{Serializer, StringRepresentable}
import fr.linkit.api.local.system.Version
import fr.linkit.engine.connection.packet.serialization.procedures.PuppetWrapperProcedure
import fr.linkit.engine.connection.packet.serialization.tree.SerialContext
import fr.linkit.engine.connection.packet.serialization.tree.nodes.StringRepresentableNode

import java.nio.file.Path

class DefaultSerializer() extends Serializer {

    private val serialContext = new SerialContext()

    override val signature: Array[Byte] = Array(4)

    override def serialize(serializable: Serializable, withSignature: Boolean): Array[Byte] = {
        val node  = serialContext.getSerialNodeForRef(serializable)
        val bytes = node.serialize(serializable, true)

        if (withSignature) signature ++ bytes else bytes
    }

    override def isSameSignature(bytes: Array[Byte]): Boolean = bytes.startsWith(signature)

    override def deserialize(bytes: Array[Byte]): Any = {
        val node = serialContext.getDeserialNodeFor(bytes.drop(1))
        node.deserialize()
    }

    override def deserializeAll(bytes: Array[Byte]): Array[Any] = {
        val node = serialContext.getDeserialNodeFor[Array[Any]](bytes.drop(1))
        node.deserialize()
    }

    def getContext: SerialContext = serialContext

    def initNetwork(network: Network): Unit = {
        if (serialContext.getNetwork != null)
            throw new IllegalStateException("Network already initialized !")
        serialContext.updateNetwork(network)
    }

    serialContext.attachFactory(StringRepresentableNode(Version))
    serialContext.attachFactory(StringRepresentableNode(PathRepresentable))
    serialContext.attachProcedure[PuppetWrapper[Serializable]](PuppetWrapperProcedure)

    private object PathRepresentable extends StringRepresentable[Path] {

        override def getRepresentation(t: Path): String = t.toString

        override def fromRepresentation(str: String): Path = Path.of(str)
    }

}