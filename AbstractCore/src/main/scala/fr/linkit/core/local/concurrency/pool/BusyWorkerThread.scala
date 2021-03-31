package fr.linkit.core.local.concurrency.pool

import fr.linkit.api.local.concurrency.EntertainedThread
import fr.linkit.api.local.system.AppLogger
import fr.linkit.core.local.concurrency.pool.BusyWorkerPool.workerThreadGroup

import java.util.concurrent.locks.LockSupport
import scala.collection.mutable.ListBuffer

/**
 * The representation of a java thread, extending from [[Thread]].
 * This class contains information that need to be stored into a specific thread class.
 * */
private[concurrency] final class BusyWorkerThread private[concurrency](target: Runnable,
                                                                       override val owner: BusyWorkerPool,
                                                                       id: Int)
        extends Thread(workerThreadGroup, target, s"${owner.name}'s Thread#${id}") with EntertainedThread {

    private              var continueWorkflow       : Boolean         = true
    private[concurrency] var isParkingForWorkflow   : Boolean         = false
    private[concurrency] var taskRecursionDepthCount: Int             = 0
    private              var currentTaskID          : Int             = -1
    private val currentTasksID                      : ListBuffer[Int] = ListBuffer()

    private[concurrency] def workflowLoop[T](parkAction: => T)(workflow: T => Unit): Unit = {
        while (continueWorkflow) {
            isParkingForWorkflow = true
            val t = parkAction
            AppLogger.error(s"$tasksId <> This thread has been unparked.")
            isParkingForWorkflow = false

            AppLogger.error(s"$tasksId <> Continue workflow...")
            workflow(t)
            AppLogger.error(s"$tasksId <> Workflow have ended !")
        }
        this.continueWorkflow = true //set it to true for future loopWorkflow
    }

    /**
     * Will execute awaitAction: T, then execute workflow(T) while field continueWorkflow is ready..
     * all alternatives will be executed before executing awaitAction.
     * */
    private[concurrency] def stopWorkflowLoop(): Unit = {
        if (!isWaitingForRecursiveTask)
            return
        this.continueWorkflow = false
        LockSupport.unpark(this)
    }

    private[concurrency] def addTaskID(id: Int): Unit = {
        currentTasksID += id
        currentTaskID = id
        isUpdated = true
    }

    private[concurrency] def removeTaskID(id: Int): Unit = {
        currentTasksID -= id
        currentTaskID = currentTasksID.lastOption.getOrElse(-1)
        isUpdated = true
    }

    def getCurrentTaskID: Int = currentTaskID

    //debug only
    private var isUpdated          = true
    private var tasksIdStr: String = _

    def tasksId: String = {
        if (currentTasksID.isEmpty)
            return s"${Console.YELLOW}[]"
        if (!isUpdated)
            return tasksIdStr

        val current = getCurrentTaskID
        val sb      = new StringBuilder(s"${Console.YELLOW}[")
        currentTasksID.foreach(taskID => {
            if (taskID == current) {
                sb.append(Console.RED)
                        .append(taskID)
                        .append(Console.YELLOW)
            } else {
                sb.append(taskID)
            }
            sb.append(',')
        })
        tasksIdStr = sb.dropRight(1) // remove last ',' char
                .append(']')
                .toString()
        tasksIdStr
    }

    override def isWaitingForRecursiveTask: Boolean = isParkingForWorkflow

    override def taskRecursionDepth: Int = taskRecursionDepthCount

}