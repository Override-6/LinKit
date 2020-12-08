package fr.overridescala.linkkit.server

import java.net.{ServerSocket, SocketException}
import java.nio.charset.Charset
import java.nio.file.{Path, Paths}

import fr.overridescala.linkkit.api.`extension`.{RelayExtensionLoader, RelayProperties}
import fr.overridescala.linkkit.api.exceptions.{RelayClosedException, RelayException}
import fr.overridescala.linkkit.api.packet.fundamental.DataPacket
import fr.overridescala.linkkit.api.packet._
import fr.overridescala.linkkit.api.system.event.EventObserver
import fr.overridescala.linkkit.api.system.{Reason, RemoteConsole, RemoteConsolesHandler, SystemPacketChannel, Version}
import fr.overridescala.linkkit.api.task.{Task, TaskCompleterHandler}
import fr.overridescala.linkkit.api.Relay
import fr.overridescala.linkkit.api.packet.channel.PacketChannel
import fr.overridescala.linkkit.api.packet.collector.{AsyncPacketCollector, PacketCollector, SyncPacketCollector}
import fr.overridescala.linkkit.server.RelayServer.Identifier
import fr.overridescala.linkkit.server.connection.{ClientConnectionThread, ConnectionsManager, SocketContainer}

import scala.util.control.NonFatal

object RelayServer {
    val Identifier = "server"

    val version: Version = Version("RelayServer", "0.3.0", stable = false)
}

class RelayServer extends Relay {

    private val serverSocket = new ServerSocket(48484)
    private val taskFolderPath = getTasksFolderPath

    @volatile private var open = false
    /**
     * For safety, prefer Relay#identfier instead of Constants.SERVER_ID
     * */
    override val identifier: String = Identifier
    override val eventObserver: EventObserver = new EventObserver
    override val extensionLoader = new RelayExtensionLoader(this, taskFolderPath)
    override val taskCompleterHandler = new TaskCompleterHandler
    override val properties: RelayProperties = new RelayProperties
    override val packetManager = new PacketManager(eventObserver.notifier)

    override val relayVersion: Version = RelayServer.version

    private[server] val notifier = eventObserver.notifier
    private var remoteConsoles: RemoteConsolesHandler = _

    val trafficHandler = new ServerTrafficHandler(this)
    val connectionsManager = new ConnectionsManager(this)

    override def scheduleTask[R](task: Task[R]): RelayTaskAction[R] = {
        ensureOpen()
        val targetIdentifier = task.targetID
        val connection = getConnection(targetIdentifier)
        if (connection == null)
            throw new NoSuchElementException(s"Unknown or unregistered relay with identifier '$targetIdentifier'")

        val tasksHandler = connection.getTasksHandler
        task.preInit(tasksHandler)
        notifier.onTaskScheduled(task)
        RelayTaskAction.of(task)
    }

    override def start(): Unit = {
        println("Current encoding is " + Charset.defaultCharset().name())
        println("Listening on port " + serverSocket.getLocalPort)
        println("Computer name is " + System.getenv().get("COMPUTERNAME"))
        println(apiVersion)
        println(relayVersion)

        open = true
        extensionLoader.loadExtensions()
        remoteConsoles = new RemoteConsolesHandler(this)

        println("Ready !")
        notifier.onReady()

        while (open) handleSocketConnection()
    }


    override def createSyncChannel(linkedRelayID: String, id: Int): PacketChannel.Sync = {
        ensureOpen()

        val targetConnection = getConnection(linkedRelayID)
        targetConnection.createSync(id)
    }


    override def createAsyncChannel(linkedRelayID: String, id: Int): PacketChannel.Async = {
        ensureOpen()

        val targetConnection = getConnection(linkedRelayID)
        targetConnection.createAsync(id)
    }

    override def createSyncCollector(id: Int): PacketCollector.Sync = {
        ensureOpen()
        new SyncPacketCollector(trafficHandler, id)
    }

    override def createAsyncCollector(id: Int): PacketCollector.Async = {
        ensureOpen()
        new AsyncPacketCollector(trafficHandler, id)
    }

    override def getConsoleOut(targetId: String): Option[RemoteConsole] = {
        val connection = getConnection(targetId)
        if (connection == null)
            return Option.empty

        Option(remoteConsoles.getOut(targetId))
    }

    override def getConsoleErr(targetId: String): Option[RemoteConsole.Err] = {
        val connection = getConnection(targetId)
        if (connection == null)
            return Option.empty

        Option(remoteConsoles.getErr(targetId))
    }

    override def close(reason: Reason): Unit =
        close(identifier, reason)


    def close(relayId: String, reason: Reason): Unit = {
        println("closing server...")
        connectionsManager.close(reason)
        serverSocket.close()

        open = false
        notifier.onClosed(relayId, reason)
        println("server closed !")
    }

    private val tempSocket = new SocketContainer(notifier, false)

    def handleRelayPointConnection(identifier: String): Unit = {

        if (connectionsManager.isNotRegistered(identifier)) {
            val socketContainer = new SocketContainer(notifier, true)
            socketContainer.set(tempSocket.get)
            connectionsManager.register(socketContainer, identifier)
            sendResponse("OK")
            return
        }

        val connection = getConnection(identifier)
        if (connection.isConnected) {
            Console.err.println("Rejected connection of a client because he gave an already registered relay identifier.")
            sendResponse("ERROR")
            return
        }

        connection.updateSocket(tempSocket.get)
        sendResponse("OK")
    }

    def getConnection(relayIdentifier: String): ClientConnectionThread = {
        ensureOpen()
        connectionsManager.getConnectionFromIdentifier(relayIdentifier)
    }

    private def handleSocketConnection(): Unit = {
        try {
            val clientSocket = serverSocket.accept()
            tempSocket.set(clientSocket)

            val identifier = ClientConnectionThread.retrieveIdentifier(tempSocket, this)
            handleRelayPointConnection(identifier)
        } catch {
            case e@(_: RelayException | _: SocketException) =>
                Console.err.println(e.getMessage)
                onException()

            case NonFatal(e) =>
                e.printStackTrace()
                onException()
        }

        def onException(): Unit = {
            sendResponse("ERROR") //send a negative response for the client initialisation handling
            close(Reason.INTERNAL_ERROR)
        }
    }

    private def getTasksFolderPath: Path = {
        val path = System.getenv().get("COMPUTERNAME") match {
            case "PC_MATERIEL_NET" => "C:\\Users\\maxim\\Desktop\\Dev\\VPS\\ClientSide\\RelayExtensions"
            case _ => "RelayExtensions/"
        }
        Paths.get(path)
    }

    private def ensureOpen(): Unit = {
        if (!open)
            throw new RelayClosedException("Relay Server have to be started !")
    }

    private def sendResponse(response: String): Unit = {
        val responsePacket = DataPacket(response)
        val coordinates = PacketCoordinates(SystemPacketChannel.SystemChannelID, "unknown", identifier)
        tempSocket.write(packetManager.toBytes(responsePacket, coordinates))
    }

    Runtime.getRuntime.addShutdownHook(new Thread(() => close(Reason.INTERNAL)))
}