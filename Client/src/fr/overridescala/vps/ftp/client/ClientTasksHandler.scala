package fr.overridescala.vps.ftp.client

import java.nio.channels.SocketChannel
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import fr.overridescala.vps.ftp.api.packet.{DataPacket, PacketChannelManager, SimplePacketChannel}
import fr.overridescala.vps.ftp.api.task.{TaskCompleterFactory, TaskExecutor, TasksHandler}

class ClientTasksHandler(socket: SocketChannel) extends TasksHandler{

    private val queue: BlockingQueue[TaskAchieverTicket] = new ArrayBlockingQueue[TaskAchieverTicket](200)
    private var currentChannelManager: PacketChannelManager = _

    override def registerTask(achiever: TaskExecutor, sessionID: Int, ownerID: String, ownFreeWill: Boolean): Unit = {
        val ticket = new TaskAchieverTicket(achiever, ownerID, sessionID, ownFreeWill)
        queue.offer(ticket)
        println("new task registered !")
    }

    def start(): Unit = {
        val thread = new Thread(() => {
            while (true) {
                println("waiting for another task to complete...")
                val ticket = queue.take()
                currentChannelManager = ticket.channel
                ticket.start()
            }
        })
        thread.setName("Client Tasks")
        thread.start()
    }

    override def handlePacket(packet: DataPacket, factory: TaskCompleterFactory, ownerID: String, socket: SocketChannel): Unit = {
        if (packet.taskID != currentChannelManager.taskID) {
            val completer = factory.getCompleter(packet)
            registerTask(completer, packet.taskID, ownerID, false)
            return
        }
        currentChannelManager.addPacket(packet)
    }

    private class TaskAchieverTicket(private val taskAchiever: TaskExecutor,
                                     private val ownerID: String,
                                     private val taskID: Int,
                                     private val ownFreeWill: Boolean) {

        val name: String = taskAchiever.getClass.getSimpleName
        private[ClientTasksHandler] val channel: SimplePacketChannel = new SimplePacketChannel(socket, ownerID, taskID)

        def start(): Unit = {
            try {
                println(s"executing $name...")
                if (ownFreeWill)
                    taskAchiever.sendTaskInfo(channel)
                taskAchiever.execute(channel)
            } catch {

                case e: Throwable => e.printStackTrace()
            }
        }

        override def toString: String = s"Ticket(name = $name," +
                s" ownerID = $ownerID," +
                s" id = $taskID," +
                s" freeWill = $ownFreeWill)"

    }

}
