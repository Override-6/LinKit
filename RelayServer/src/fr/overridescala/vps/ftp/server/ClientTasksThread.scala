package fr.overridescala.vps.ftp.server

import java.io.Closeable
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import fr.overridescala.vps.ftp.api.packet.{DataPacket, PacketChannelManager}

class ClientTasksThread() extends Thread with Closeable {

    private val queue: BlockingQueue[TaskTicket] = new ArrayBlockingQueue[TaskTicket](200)
    @volatile private var open = false
    @volatile private var currentChannelManager: PacketChannelManager = _

    override def run(): Unit = {
        open = true
        while (open) {
            val ticket = queue.take()
            currentChannelManager = ticket.channel
            ticket.start()
        }
    }

    override def close(): Unit = {
        open = false
        stop()
    }

    def injectPacket(packet: DataPacket): Unit =
        currentChannelManager.addPacket(packet)


    def addTicket(ticket: TaskTicket): Unit = {
        queue.add(ticket)
    }

    def tasksIDMatches(packet: DataPacket): Boolean = {
        currentChannelManager != null && packet.taskID == currentChannelManager.taskID
    }
}
