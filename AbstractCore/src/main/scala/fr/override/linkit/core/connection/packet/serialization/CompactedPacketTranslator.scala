package fr.`override`.linkit.core.connection.packet.serialization

import fr.`override`.linkit.core.connection
import fr.`override`.linkit.core.connection.network.cache
import fr.`override`.linkit.core.connection.network.cache.collection
import fr.`override`.linkit.core.connection.{network, packet}
import fr.`override`.linkit.api.Relay
import fr.`override`.linkit.api.connection.network.cache.SharedCacheHandler
import fr.`override`.linkit.api.connection.network.cache.collection.SharedCollection
import fr.`override`.linkit.api.connection.packet._
import fr.`override`.linkit.api.connection.packet.serialization.{ObjectSerializer, PacketSerializationResult, PacketTranslator}
import org.jetbrains.annotations.Nullable

import scala.util.control.NonFatal

class CompactedPacketTranslator(relay: Relay) extends PacketTranslator { //Notifier is accessible from api to reduce parameter number in (A)SyncPacketChannel

    def toPacketAndCoords(bytes: Array[Byte]): (Packet, PacketCoordinates) = {
        SmartSerializer.deserialize(bytes).swap
    }

    def fromPacketAndCoords(packet: Packet, coordinates: PacketCoordinates): PacketSerializationResult = {
        val result = SmartSerializer.serialize(packet, coordinates)
        relay.securityManager.hashBytes(result.bytes)
        result
    }

    def completeInitialisation(cache: cache.AbstractSharedCacheManager): Unit = {
        return
        SmartSerializer.completeInitialisation(cache)
    }

    def blackListFromCachedSerializer(target: String): Unit = {
        SmartSerializer.blackListFromCachedSerializer(target)
    }

    private object SmartSerializer {
        private val rawSerializer = RawObjectSerializer
        @Nullable
        @volatile private var cachedSerializer: packet.serialization.ObjectSerializer = _ //Will be instantiated once connection with the server is handled.
        @Nullable
        @volatile private var cachedSerializerWhitelist: collection.SharedCollection[String] = _

        def serialize(packet: Packet, coordinates: PacketCoordinates): PacketSerializationResult = {
            //Thread.dumpStack()
            val serializer = if (initialised) {
                val whiteListArray = cachedSerializerWhitelist.toArray
                coordinates.determineSerializer(whiteListArray, rawSerializer, cachedSerializer)
            } else {
                rawSerializer
            }
            try {
                //println(s"Serializing $packet, $coordinates in thread ${Thread.currentThread()} with serializer ${serializer.getClass.getSimpleName}")
                val bytes = serializer.serialize(Array(coordinates, packet))
                PacketSerializationResult(packet, coordinates, serializer, bytes)
            } catch {
                case NonFatal(e) =>
                    throw PacketException(s"Could not serialize packet and coordinates $packet, $coordinates.", e)
            }
        }

        def deserialize(bytes: Array[Byte]): (PacketCoordinates, Packet) = {
            val serializer = if (rawSerializer.isSameSignature(bytes)) {
                rawSerializer
            } else if (!initialised) {
                throw new IllegalStateException("Received cached serialisation signature but this packet translator is not ready to handle it.")
            } else {
                cachedSerializer
            }
            val array = try {
                serializer.deserializeAll(bytes)
            } catch {
                case NonFatal(e) =>
                    throw PacketException(s"Could not deserialize bytes ${new String(bytes)} to packet.", e)

            }
            //println(s"Deserialized ${array.mkString("Array(", ", ", ")")}")
            (array(0).asInstanceOf[PacketCoordinates], array(1).asInstanceOf[Packet])
        }

        def completeInitialisation(cache: network.cache.AbstractSharedCacheManager): Unit = {
            if (cachedSerializer != null)
                throw new IllegalStateException("This packet translator is already fully initialised !")

            cachedSerializer = new CachedObjectSerializer(cache)
            cachedSerializerWhitelist = cache.get(15, connection.network.cache.collection.SharedCollection[String])
            cachedSerializerWhitelist.add(relay.identifier)
        }

        def initialised: Boolean = cachedSerializerWhitelist != null

        def blackListFromCachedSerializer(target: String): Unit = {
            if (cachedSerializerWhitelist != null)
                cachedSerializerWhitelist.remove(target)
        }
    }

}
