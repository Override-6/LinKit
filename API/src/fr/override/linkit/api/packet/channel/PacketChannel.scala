package fr.`override`.linkit.api.packet.channel

import fr.`override`.linkit.api.`extension`.packet.PacketFactory
import fr.`override`.linkit.api.packet.{Packet, PacketContainer, PacketCoordinates, TrafficHandler}
import fr.`override`.linkit.api.system.{CloseReason, JustifiedCloseable}

//TODO Doc
/**
 * this class link two Relay between them. As a Channel, it can send packet, or wait until a packet was received
 *
 * @see [[PacketChannel]]
 * */
abstract class PacketChannel(handler: TrafficHandler) extends JustifiedCloseable with PacketContainer {

    val connectedID: String
    val ownerID: String = handler.relayID

    val coordinates: PacketCoordinates = PacketCoordinates(identifier, connectedID, ownerID)

    handler.register(this)

    override def close(reason: CloseReason): Unit = handler.unregister(identifier, reason)

    def sendPacket(packet: Packet): Unit = handler.sendPacket(packet, coordinates)

}

object PacketChannel {

    abstract class Async(handler: TrafficHandler) extends PacketChannel(handler) {
        def onPacketReceived(consumer: Packet => Unit): Unit
    }

    abstract class Sync(traffic: TrafficHandler) extends PacketChannel(traffic) {

        /**
         * Waits until a data packet is received and concerned about this task.
         *
         * @return the received packet
         * @see [[DataPacket]]
         * */
        def nextPacket(): Packet

        def nextPacketAsP[P <: Packet](): P = nextPacket().asInstanceOf[P]

        def nextPacket[P <: Packet](classOfP: Class[P]): P = nextPacketAsP()

        def nextPacket[P <: Packet](factoryOfP: PacketFactory[P]): P = nextPacket(factoryOfP.packetClass)

        /**
         * @return true if this channel contains stored packets. In other words, return true if [[nextPacketAsP]] will not wait
         * */
        def haveMorePackets: Boolean
    }

}