package fr.overridescala.vps.ftp.client.cli.commands

import fr.overridescala.vps.ftp.api.Relay
import fr.overridescala.vps.ftp.api.task.tasks.{DownloadTask, FileInfoTask, UploadTask}
import fr.overridescala.vps.ftp.api.transfer.{FileDescription, TransferDescription, TransferDescriptionBuilder}
import fr.overridescala.vps.ftp.client.cli.{CommandException, CommandExecutor}
import fr.overridescala.vps.ftp.client.cli.CommandUtils._

/**
 * syntax : <p>
 * upload | download -s "sourceP path" -t "target identifier" -d "destination path"
 * */
class TransferCommand private(private val relay: Relay,
                      private val isDownload: Boolean) extends CommandExecutor {


    override def execute(args: Array[String]): Unit = {
        checkArgs(args)
        val target = argAfter(args, "-t")
        val sourcePath = argAfter(args, "-s")
        val dest = argAfter(args, "-d")
        val sourceP =
            if (isDownload) relay.scheduleTask(FileInfoTask.concoct(target, sourcePath)).complete()
            else FileDescription.fromLocal(sourcePath)
        //abort execution if sourceP could not be found.
        if (sourceP == null)
            return

        val transferDescription = new TransferDescriptionBuilder {
            source = sourceP
            targetID = target
            destination = dest
        }
        val concoctor: TransferDescription => TaskConcoctor[_] = if (isDownload) DownloadTask.concoct else UploadTask.concoct
        relay.scheduleTask(concoctor(transferDescription))
                .complete()
    }

    def checkArgs(args: Array[String]): Unit = {
        if (args.length != 6)
            throw new CommandException("argument length must be 6")
        checkArgsContains(args, "-t", "-s", "-d")
    }



}

object TransferCommand {
    def download(relay: Relay): TransferCommand =
        new TransferCommand(relay, true)

    def upload(relay: Relay): TransferCommand =
        new TransferCommand(relay, false)
}
