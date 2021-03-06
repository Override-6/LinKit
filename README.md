![Cover](RCover.png)
 A personal project to easily synchronise and link programs.  

This project is designed to help the user to quickly write the network side of an application (for games, apps...).
LinKit offers an entire networking library, that can be extended and controlled from a 'Relay'  
The project is mainly write in scala, and some classes are written in Java.  

## Table of contents
* [Notes and General information](https://github.com/Override-6/LinKit/#notes-and-general-information)
* [The API](https://github.com/Override-6/LinKit/#the-api)
* [The Client](https://github.com/Override-6/LinKit/#the-client)
* [The Server](https://github.com/Override-6/LinKit/#the-server)
* [Features](https://github.com/Override-6/LinKit/#features)
* [How to extend](https://github.com/Override-6/LinKit/#how-to-extend)
* [How to configure](https://github.com/Override-6/LinKit/#how-to-configure)
* [Setup](https://github.com/Override-6/LinKit/#setup)
* [Acknowledgements](https://github.com/Override-6/LinKit/#acknowledgements)

## Notes and General information
This Readme contains the general information to create your own project with this program  
If you want further details about the [API](https://github.com/Override-6/LinKit/blob/master/API), the [RelayServer](https://github.com/Override-6/LinKit/blob/master/RelayServer) or the [RelayPoint](https://github.com/Override-6/LinKit/blob/master/RelayPoint), you can open theirs respective folders.  
This project quickly progresses, and, as long as the project stands in beta, irreversible changes would often be made.  
Such as refactoring, movement or deletion of different classes, packages, folders or methods, Illogical feature operations, bugs or even redesigning some aspect.  
Some Scala practices would be a real nightmare to use in Java, such as Builders, so they have a Java version in order to be more user-friendly with java developers.

### Versioning
The current versioning system is the [SemVer](https://semver.org/), but, during the beta phase of this project, it will not being respected.  
This way, the first number is always 0, any feature or irreversible change would increment the middle number, and small reversible patches increments the last number.  

#### During beta phase : 
0.x.y
* x feature, irreversible internal changes or remote changes such as packet protocol or communication 
* y patches, internal bugfixes, that are fully reversible  

No objectives are scheduled in order to exit the beta phase.

## The API
The API is a bag of features and definitions of behavior that a Relay implementation must support.  
Some features are pre-implemented and handled by the api, such as the [extension](TODO), the [event handling system](TODO) or the [Packet handling](TODO) and [Task scheduling](TODO) which are partially implemented.  
All of those features are accessible from the Relay interface.  

(see [API README](https://github.com/Override-6/LinKit/tree/master/API) to get further details)  

### Packet handling

Protocol Used : TCP

[Packet](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/packet/Packet.scala) implementations are simply serialised / deserialised using custom serialisation protocol. Any packet is serialised/deserialised with the [PacketTranslator](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/packet/serialization/PacketTranslator.scala), that handles the byte sequence of packet content __and__ their coordinates.

## The Client  
The Client, or RelayPoint, is one implementation of the Relay interface, which can connect to any server implementation, as long as they comply with the same mutual initialisation rules.  
In order to instantiate a RelayPoint, you need a RelayPointConfiguration.  
The different option explications of a RelayPointConfiguration can be find in the [RelayPoint README](https://github.com/Override-6/LinKit/tree/master/RelayPoint), but here, we will focus on how to create a default RelayPoint by using its builder :  

```scala
val relayPoint: RelayPoint = new RelayPointBuilder {
    override var serverAddress: InetSocketAddress = serverAddress
    override var identifier: String = relayPointIdentifier
} //No need to write .build() thanks to the scala implicits !
```

Here you have the JavaBuilder version for Java users :
```java
RelayPoint relayPoint = RelayPointBuilder
    .javaBuilder(serverAddress, relayPointIdentifier)
    .build();
relayPoint.start();
```

## The Server
The Server, or RelayServer, is an implementation of the library that handles multiple connections, it is meant to faciliate packet exchange between clients, but must still capable that it can behaves like a client.  
A server implementation is the center of the [network](TODO). Even if its network entity does not provide a lot more features, the server must ensure that every thing works correctly, and handles the connection informations and packet sharing.  

## Features
Here is a list of features that are already supported

* Scoped Packet channels
* Relay Extensions / Extension Fragments / Remote Fragments
* Automatic reconnection
* Shared cache (instance, collection, map) between connections
* Schedulable tasks
* Smart concurrency handling, with providable threads.

TODO List
* Optimised big transfert channel for streaming
* Enhance the shared cache feature
* Optimised reactive and bindable channels for game networking
* RemoteObject wrappers (a kind of RMI, but for automatic behaviours) ex: game networking

## How to extend 
You can extend the Linkit program by adding a plugin jar into the RelayExtension folder.  
In order to define your jar as a RelayExtension, you must create a `extension.property` resource file referencing yout main class, that must extends from [`RelayExtension`](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/extension/RelayExtension.scala).
This class, defines one abstract method `onEnable`, and two overridable methods `onLoad` & `onDisable`. It also defines protected put/getFragment methods.
If you want your extension to be usable by other internal extensions, you can put an [ExtensionFragment](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/extension/fragment/ExtensionFragment.scala).
Also, If you would like to have a fragment extension to be usable by other relays, through the network, you can put a [RemoteFragment](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/extension/fragment/RemoteFragment.scala). The putFragment method is only usable during [Load phase](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/extension/LoadPhase.java)

Here is a scala example for an extension plugin main class :
```scala
class EasySharing(relay: Relay) extends RelayExtension(relay) {

    override def onLoad(): Unit = {
        //Putting remote fragment to let other relays manage this computer's clipboard
        putFragment(new RemoteClipboard())
    }

    override def onEnable(): Unit = {
        //Getting the CommandManager ExtensionFragment, putted by the extension "ControllerExtension". 
        val commandManager = getFragmentOrAbort(classOf[ControllerExtension], classOf[CommandManager])
        commandManager.register("paste", new RemotePasteCommand(relay))
    }
}
```

## How to configure
In order to quickly define some custom behaviour for the implementations, the Relay interface defines a configuration value.  
This value must be present in Relay's implementation constructors.  
The default RelayConfiguration interface defines basic parameters, and the implementation of this trait for RelayPoint and RelayServer defines more specific options to configure the implementations.

Here is an exaustive list of options contained in the default RelayPointConfiguration

* enable/disable Extensions Folder loading. (would not load plugins contained in the extension folder if disabled)
* enable/disable task handling
* enable/disable event handling (Note: This option does nothing because their is no event handling feature currently)
* enable/disable Remote Consoles (nothing would be print on the current console, but this relay can still send prints)
* checkReceivedPacketTargetID (FOR REMOVAL) if true, all packet will be scanned in order to ensure that the packet has been sent to the right relay.
* taskQueueSize defines how many tasks can wait in the queue to be executed
* maxPacketLength (FOR REMOVAL)
* defaultContainerPacketCacheSize (FOR REMOVAL)
* maxPacketContainerCacheSize (FOR REMOVAL)

You can find the list of [RelayPoint](https://github.com/Override-6/LinKit/tree/master/RelayPoint) and [RelayServer](https://github.com/Override-6/LinKit/tree/master/RelayServer) configuration options in their respective readme.  

## Setup
The setup is very simple; you just have to download / compile the source code of RelayPoint or RelayServer, then create a RelayPoint/Server instance with a RelayPoint/ServerBuilder. In order to start your relay, you'll must call the Relay#start method in the [RelayWorkerThreadPool](https://github.com/Override-6/LinKit/blob/master/API/src/fr/override/linkit/api/concurrency/RelayWorkerThreadPool.scala) of the relay. In order to retrieve the thread pool execution, simply use Relay#runLater.

Here is an example for setting up the client : 
```scala
val relayPoint: RelayPoint = new RelayPointBuilder {
    override var serverAddress: InetSocketAddress = serverAddress
    override var identifier: String = relayPointIdentifier
    //setting other optional configuration options...
}

relayPoint.runLater {
    relayPoint.start()
    println("Client started !") 
}
```

## Acknowledgements
I owe a big part of my knowledge to a discord server named [ReadTheDocs](https://readthedocs-fr.github.io/), and some tutorials i found on internet.
Here is a non-ordered list of different people that helped me writing the project, or helped me get more trained with programmation :

- TheElectronWill
- Akami
- Hokkayado
- Mesabloo
- MinusKube
- Emalios

thanks for you <3
