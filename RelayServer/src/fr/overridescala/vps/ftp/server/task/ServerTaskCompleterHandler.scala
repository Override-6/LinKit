package fr.overridescala.vps.ftp.server.task

import fr.overridescala.vps.ftp.api.Relay
import fr.overridescala.vps.ftp.api.packet.TaskInitPacket
import fr.overridescala.vps.ftp.api.task.tasks._
import fr.overridescala.vps.ftp.api.task.{TaskCompleterHandler, TasksHandler}
import fr.overridescala.vps.ftp.api.transfer.TransferDescription
import fr.overridescala.vps.ftp.api.utils.Utils
import fr.overridescala.vps.ftp.server.task.ServerTaskCompleterHandler.TempFolder

import scala.collection.mutable

class ServerTaskCompleterHandler(private val tasksHandler: ServerTasksHandler,
                                 private val server: Relay) extends TaskCompleterHandler {

    private lazy val completers: mutable.Map[String, (TaskInitPacket, TasksHandler, String) => Unit]
    = new mutable.HashMap[String, (TaskInitPacket, TasksHandler, String) => Unit]()

    override def handleCompleter(initPacket: TaskInitPacket, senderId: String): Unit =
        if (testTransfer(initPacket, senderId) && testOther(initPacket, senderId))
            testMap(initPacket, senderId)

    override def putCompleter(taskType: String, supplier: (TaskInitPacket, TasksHandler, String) => Unit): Unit =
        completers.put(taskType, supplier)

    private def handleUpload(uploadDesc: TransferDescription, ownerID: String, taskID: Int): Unit = {
        val desc = TransferDescription.builder()
                .setSource(uploadDesc.source)
                .setDestination(uploadDesc.destination)
                .setTargetID(ownerID)
                .build()
        if (!uploadDesc.targetID.equals(server.identifier)) {
            val redirectedTransferDesc = TransferDescription.builder()
                    .setTargetID(uploadDesc.targetID)
                    .setDestination(TempFolder)
                    .setSource(uploadDesc.source)
                    .build()
            new DownloadTask(tasksHandler, desc).queue(_ => {
                new UploadTask(tasksHandler, redirectedTransferDesc)
            }, _, taskID)
            return
        }

        new DownloadTask(tasksHandler, desc).queue(_, _, taskID)
    }

    private def handleDownload(downloadDesc: TransferDescription, ownerID: String, taskID: Int): Unit = {
        val desc = TransferDescription.builder()
                .setSource(downloadDesc.source)
                .setDestination(downloadDesc.destination)
                .setTargetID(ownerID)
                .build()
        if (!downloadDesc.targetID.equals(server.identifier)) {
            val redirectedTransferDesc = TransferDescription.builder()
                    .setTargetID(downloadDesc.targetID)
                    .setDestination(TempFolder)
                    .setSource(downloadDesc.source)
                    .build()
            new DownloadTask(tasksHandler, redirectedTransferDesc).queue(_ => {
                new UploadTask(tasksHandler, desc)
            }, _, taskID)
            return
        }
        new UploadTask(tasksHandler, desc).queue(_, _, taskID)
    }

    private def testMap(initPacket: TaskInitPacket, senderId: String): Unit = {
        val supplier = completers(initPacket.taskType)
        supplier(initPacket, tasksHandler, senderId)
    }

    private def testTransfer(packet: TaskInitPacket, senderId: String): Boolean = {
        val taskType = packet.taskType
        val taskID = packet.taskID
        val content = packet.content
        taskType match {
            case UploadTask.TYPE =>
                handleUpload(Utils.deserialize(content), senderId, taskID)
                false

            case DownloadTask.TYPE =>
                handleDownload(Utils.deserialize(content), senderId, taskID)
                false
            case _ => true
        }
    }

    private def testOther(packet: TaskInitPacket, senderID: String): Boolean = {
        val taskType = packet.taskType
        val content = packet.content
        val contentString = new String(content)
        val task = taskType match {
            case PingTask.TYPE =>
                new PingTask.PingCompleter

            case FileInfoTask.TYPE =>
                val pair: (String, _) = Utils.deserialize(content)
                new FileInfoTask.FileInfoCompleter(pair._1)

            case CreateFileTask.TYPE =>
                new CreateFileTask.CreateFileCompleter(new String(content.slice(1, content.length)), content(0) == 1)

            case StressTestTask.TYPE =>
                new StressTestTask.StressTestCompleter(contentString.toLong)

            case _ => null
        }
        if (task == null)
            return true
        tasksHandler.registerTask(task, packet.taskID, false, packet.targetId, senderID)
        false
    }

}

object ServerTaskCompleterHandler {
    val TempFolder = "/home/override/VPS/FileTransferer/Temp"
}
