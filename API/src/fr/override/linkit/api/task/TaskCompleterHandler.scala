/*
 * Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can only use it for personal uses, studies or documentation.
 * You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 * ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 * Please contact maximebatista18@gmail.com if you need additional information or have any
 * questions.
 */

package fr.`override`.linkit.api.task

import fr.`override`.linkit.api.exception.TaskException
import fr.`override`.linkit.api.packet.DedicatedPacketCoordinates
import fr.`override`.linkit.api.packet.fundamental.TaskInitPacket

import scala.collection.mutable

/**
 * handles TaskCompleters from
 * an initialization DataPacket
 * */
class TaskCompleterHandler {

    private val completers: mutable.Map[String, (TaskInitPacket, DedicatedPacketCoordinates) => TaskExecutor] = new mutable.HashMap()

    /**
     * @param initPacket the initialization packet for completer.
     * @param tasksHandler the handler that will schedule the completer
     * @throws TaskException if no completer where found from this packet header
     *
     * @see [[TaskInitPacket]]
     * */
    def handleCompleter(initPacket: TaskInitPacket, coords: DedicatedPacketCoordinates, tasksHandler: TasksHandler): Unit = {
        val taskType = initPacket.taskType
        val taskID = coords.injectableID
        val targetID = coords.senderID

        val completerOpt = completers.get(taskType)
        if (completerOpt.isEmpty)
            throw new TaskException(s"Could not find completer of type '$taskType'")

        val completer = completerOpt.get.apply(initPacket, coords)
        tasksHandler.schedule(completer, taskID, targetID, ownFreeWill = false)
    }

    /**
     * To be extensible, the user need to add Completer suppliers to handlers in order to inject his own tasks into the program.
     * @param taskType the task type for what the supplier will be called.
     * @param supplier this lambda takes a [[TaskInitPacket]] the Tasks Handler and the init packet sender identifier
     *                 and the task owner identifier
     * */
    def register(taskType: String, supplier: (TaskInitPacket, DedicatedPacketCoordinates) => TaskExecutor): Unit = {
        completers.put(taskType, supplier)
    }

    def isRegistered(taskType: String): Boolean =
        completers.contains(taskType)


}
