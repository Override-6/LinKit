package fr.overridescala.vps.ftp.server.task

import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.Socket

import fr.overridescala.vps.ftp.api.exceptions.TaskException
import fr.overridescala.vps.ftp.api.packet.SimplePacketChannel
import fr.overridescala.vps.ftp.api.task.{Task, TaskExecutor}
import fr.overridescala.vps.ftp.server.RelayServer

import scala.util.control.NonFatal

case class TaskTicket(private val executor: TaskExecutor,
                      private val taskID: Int,
                      private val targetID: String,
                      private val senderIdentifier: String,
                      private val socket: Socket,
                      private val relay: RelayServer,
                      private val ownFreeWill: Boolean) {

    var channel: SimplePacketChannel = relay.createChannelAndManager(targetID, taskID)
    val taskName: String = executor.getClass.getSimpleName

    def abort(): Unit = {
        notifyExecutor()
        executor match {
            case task: Task[_] =>
                val errorMethod = task.getClass.getMethod("error", classOf[String])
                errorMethod.setAccessible(true)
                try {
                    errorMethod.invoke(task, "Task aborted from an external handler")
                } catch {
                    case e: InvocationTargetException if e.getCause.isInstanceOf[TaskException] => Console.err.println(e.getMessage)
                    case e: InvocationTargetException => e.getCause.printStackTrace()
                    case NonFatal(e) => e.printStackTrace()
                }
            case _ =>
        }
    }


    def start(): Unit = {
        try {
            println(s"executing $taskName...")
            executor.init(relay.packetManager, channel)
            if (ownFreeWill) {
                channel.sendInitPacket(executor.initInfo)
            }
            executor.execute()
            notifyExecutor()
            println(s"$taskName completed !")
        } catch {
            // Do not prints those exceptions : they are normal errors
            // lifted when a task is brutally aborted via Thread.stop
            case _: IllegalMonitorStateException =>
            case _: InterruptedException =>
            case e: IOException if e.getMessage != null && e.getMessage.equalsIgnoreCase("Broken pipe") =>

            case NonFatal(e) => e.printStackTrace()
        }
    }

    private def notifyExecutor(): Unit = executor.synchronized {
        executor.notifyAll()
    }

}
