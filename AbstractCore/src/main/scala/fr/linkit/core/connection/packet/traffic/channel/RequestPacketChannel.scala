package fr.linkit.core.connection.packet.traffic.channel

import fr.linkit.api.connection.packet.traffic.{ChannelScope, PacketInjectableFactory, PacketInjection}
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet}
import fr.linkit.core.connection.packet.fundamental.RefPacket.AnyRefPacket
import fr.linkit.core.connection.packet.traffic.channel.RequestPacketChannel.{Request, Response, ResponseSubmitter}
import fr.linkit.core.local.concurrency.BusyWorkerPool
import fr.linkit.core.local.system.AppLogger
import fr.linkit.core.local.utils.ConsumerContainer
import fr.linkit.core.local.utils.ScalaUtils.ensureType

import java.util.NoSuchElementException
import java.util.concurrent.BlockingQueue
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

class RequestPacketChannel(scope: ChannelScope) extends AbstractPacketChannel(scope) {

    private val requests            = mutable.LinkedHashMap.empty[Long, Request]
    private val requestConsumers    = ConsumerContainer[(Packet, DedicatedPacketCoordinates, ResponseSubmitter)]()
    @volatile private var requestID = 0

    override def handleInjection(injection: PacketInjection): Unit = {
        val packets = injection.getPackets
        val coords  = injection.coordinates
        AppLogger.debug(s"HANDLING $packets")

        packets.foreach {
            case AnyRefPacket((id: Int, packet: Packet)) =>
                val submitter = new ResponseSubmitter(id, scope, coords.senderID)
                requestConsumers.applyAllAsync((packet, coords, submitter))

            case AnyRefPacket((id: Long, packet: Packet)) =>
                val submitter = new ResponseSubmitter(id, scope, coords.senderID)
                requestConsumers.applyAllAsync((packet, coords, submitter))

            case response: Response =>
                requests.get(response.id) match {
                    case Some(request) => request.addResponse(response)
                    case None          => throw new NoSuchElementException(s"response.id not found (${response.id}) ($requests)")
                }

        }
    }

    def addRequestListener(callback: (Packet, DedicatedPacketCoordinates, ResponseSubmitter) => Unit): Unit = {
        requestConsumers += (tuple => callback(tuple._1, tuple._2, tuple._3))
    }

    def startRequest(packet: Packet, targets: String*): Request = {
        send(packet, scope.sendTo(_, targets: _*))
    }

    def broadcastRequest(packet: Packet): Request = {
        send(packet, scope.sendToAll)
    }

    private def send(packet: Packet, send: Packet => Unit): Request = {
        val pool = BusyWorkerPool.checkCurrentIsWorker()

        val requestID = nextRequestID
        val request   = Request(requestID, pool.newBusyQueue)

        send(AnyRefPacket(requestID, packet))
        requests.put(requestID, request)
        request
    }

    private def nextRequestID: Int = {
        requestID += 1
        requestID
    }

}

object RequestPacketChannel extends PacketInjectableFactory[RequestPacketChannel] {

    override def createNew(scope: ChannelScope): RequestPacketChannel = new RequestPacketChannel(scope)

    case class Request(id: Long, queue: BlockingQueue[Response]) {

        private val responseConsumer = ConsumerContainer[Response]()

        def nextResponse: Response = {
            AppLogger.debug(s"Waiting for response... ($id)")
            queue.take()
        }

        def addOnResponseReceived(callback: Response => Unit): Unit = {
            responseConsumer += callback
        }

        private[RequestPacketChannel] def addResponse(response: Response): Unit = {
            AppLogger.error("ADDING RESPONSE")
            //Thread.dumpStack()
            queue.add(response)
            responseConsumer.applyAllAsync(response)
            AppLogger.error("RESPONSE ADDED")
        }

    }

    class ResponseSubmitter(id: Long, scope: ChannelScope, requester: String) {

        private val packets            = ListBuffer.empty[Packet]
        private val properties         = mutable.HashMap.empty[String, Serializable]
        @volatile private var isSubmit = false

        def addPacket(packet: Packet): this.type = {
            ensureNotSubmit()
            packets += packet
            AppLogger.debug("packets = " + packets)
            this
        }

        def addPackets(packets: Packet*): this.type = {
            ensureNotSubmit()
            this.packets ++= packets
            this
        }

        def putProperty(name: String, value: Serializable): this.type = {
            ensureNotSubmit()
            properties.put(name, value)
            this
        }

        def submit(): Unit = {
            ensureNotSubmit()
            val snapshot = Response(id, packets.toArray, properties.toMap, scope.writer.supportIdentifier)
            AppLogger.debug(s"Submitting response ($id)...")
            scope.sendTo(snapshot, requester)
            AppLogger.debug("Response submit !")
            isSubmit = true
        }

        private def ensureNotSubmit(): Unit = {
            if (isSubmit)
                throw new IllegalStateException("Response was already sent.")
        }
    }

    case class Response(id: Long,
                        packets: Array[Packet],
                        private val properties: Map[String, Serializable],
                        answerer: String) extends Packet { self =>

        private var packetIndex = 0

        def getProperty(name: String): Serializable = properties(name)

        @throws[NoSuchElementException]("If this method is called more times than packet array's length")
        def nextPacket[P <: Packet : ClassTag](): P = {
            if (packetIndex >= packets.length)
                throw new NoSuchElementException()
            val packet = packets(packetIndex)
            packetIndex += 1
            ensureType[P](packet)
        }

    }

}
