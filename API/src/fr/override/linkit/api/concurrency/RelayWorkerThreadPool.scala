package fr.`override`.linkit.api.concurrency

import java.util.concurrent.{BlockingQueue, Executors, ThreadFactory}

import fr.`override`.linkit.api.concurrency.RelayWorkerThreadPool.{WorkerThread, checkCurrentIsWorker, providers}
import fr.`override`.linkit.api.exception.IllegalThreadException

import scala.util.control.NonFatal

class RelayWorkerThreadPool() extends AutoCloseable {

    val factory: ThreadFactory = new WorkerThread(_, this)
    private val executor = Executors.newFixedThreadPool(3, factory)

    //The different tasks to make
    private val workQueue = extractWorkQueue()
    private var closed = false
    private val providerLocks = new ProvidersLock

    def runLater(action: => Unit): Unit = {
        runLater(_ => action)
    }

    def runLater(action: Unit => Unit): Unit = {
        if (!closed) {
            executor.submit((() => {
                try {
                    //println(s"Making action...($currentThread)")
                    action(null)
                    //println(s"Action made ! (queueLength: ${workQueue.size()}, current: $currentThread)")
                } catch {
                    case NonFatal(e) => e.printStackTrace()
                }
            }): Runnable)
            //if there is one provided thread that is waiting for a new task to be performed, it would instantly execute the current task.
            providerLocks.notifyOneProvider()
        }
        //println(s"RunLater submitted ! (queueLength: ${workQueue.size()}, current: $currentThread)")
    }


    def provideWhile(check: => Boolean): Unit = {
        checkCurrentIsWorker()

        //println(s"Providing in $currentThread")
        providers += 1
        //println(s"Total providers are : $providers")
        while (!workQueue.isEmpty && check) {
            val task = workQueue.poll()
            if (task != null)
                task.run()
        }
        providers -= 1
        //println(s"End Of Provide $currentThread")
        //println(s"Total providers are now : $providers")
    }

    def provideAllWhileThenWait(lock: AnyRef, check: => Boolean): Unit = {
        //println(s"Providing on lock ($lock) and condition")
        provideWhile(check)

        if (check) { //we may still need to provide
            providerLocks.addProvidingLock(lock)
            while (check) {
                //println(s"CONTINUING... ($lock, $currentThread)")
                lock.synchronized {
                    //println(s"WAiTING ($lock, $currentThread)")
                    if (check)// because of the synchronisation block, the check value may be true, so
                        lock.wait()
                }
                //println(s"CONTINUED ! ($lock, $currentThread)")
                provideWhile(check)
            }
            providerLocks.removeProvidingLock()
        }
    }

    def provide(millis: Long): Unit = {
        checkCurrentIsWorker()

        var totalProvided: Long = 0
        while (!workQueue.isEmpty && totalProvided <= millis) {
            val t0 = now()
            workQueue.take().run()
            val t1 = now()
            totalProvided += (t1 - t0)
        }
        val toWait = millis - totalProvided
        if (toWait > 0) {
            val waited = timedWait(getClass, toWait)
            if (waited < toWait)
                provide(millis)
        }
    }

    override def close(): Unit = {
        closed = true
        executor.shutdownNow()
    }

    def extractWorkQueue(): BlockingQueue[Runnable] = {
        val clazz = executor.getClass
        val field = clazz.getDeclaredField("workQueue")
        field.setAccessible(true)
        field.get(executor).asInstanceOf[BlockingQueue[Runnable]]
    }

    def newProvidedQueue[A]: BlockingQueue[A] = {
        new ProvidedBlockingQueue[A](this)
    }
}

object RelayWorkerThreadPool {

    val workerThreadGroup: ThreadGroup = new ThreadGroup("Relay Worker")
    private var activeCount = 1
    @volatile var providers = 0

    /**
     * This method may execute the given action into the current thread pool.
     * If the current execution thread does not extends from [[WorkerThread]], this would mean that,
     * we are not running into a thread that is owned by the Relay concurrency system. Therefore, the action
     * may be performed as a synchronized action.
     *
     * @param action the action to perform
     * */
    def smartRun(action: => Unit): Unit = {
        def makeAction(): Unit = action

        ifCurrentOrElse(_.runLater(makeAction()), makeAction())
    }

    def checkCurrentIsWorker(): Unit = {
        if (!isCurrentWorkerThread)
            throw new IllegalThreadException("This action must be performed in a Packet Worker thread !")
    }

    def checkCurrentIsWorker(msg: String): Unit = {
        if (!isCurrentWorkerThread)
            throw new IllegalThreadException(s"This action must be performed in a Packet Worker thread ! ($msg)")
    }

    def checkCurrentIsNotWorker(): Unit = {
        if (isCurrentWorkerThread)
            throw new IllegalThreadException("This action must not be performed in a Packet Worker thread !")
    }

    def checkCurrentIsNotWorker(msg: String): Unit = {
        if (isCurrentWorkerThread)
            throw new IllegalThreadException(s"This action must not be performed in a Packet Worker thread ! ($msg)")
    }

    def isCurrentWorkerThread: Boolean = {
        Thread.currentThread().getThreadGroup == workerThreadGroup
    }

    def smartProvide(asLongAs: => Boolean): Unit = {
        ifCurrentOrElse(_.provideWhile(asLongAs), ())
    }

    def smartWait(lock: AnyRef, asLongAs: => Boolean): Unit = {
        //println("Performing smart wait...")
        ifCurrentOrElse(_ => s"Providing ${Thread.currentThread()}", s"Waiting ${Thread.currentThread()}")
        ifCurrentOrElse(_.provideAllWhileThenWait(lock, asLongAs), lock.synchronized(lock.wait()))
    }

    def smartWait(lock: AnyRef, minTimeOut: Long): Unit = {
        ifCurrentOrElse(_.provide(minTimeOut), lock.synchronized(lock.wait(minTimeOut)))
    }

    def ifCurrentOrElse[A](ifCurrent: RelayWorkerThreadPool => A, orElse: => A): A = {
        val pool = RelayWorkerThreadPool.currentThreadPool()

        if (pool.isDefined) {
            ifCurrent(pool.get)
        } else {
            orElse
        }
    }

    def currentThreadPool(): Option[RelayWorkerThreadPool] = {
        Thread.currentThread() match {
            case worker: WorkerThread => Some(worker.owner)
            case _ => None
        }
    }

    def smartWait(lock: AnyRef, millis: Int): Unit = {
        ifCurrentOrElse(_.provide(millis), lock.synchronized(lock.wait(millis)))
    }

    class WorkerThread private[RelayWorkerThreadPool](target: Runnable,
                                                      private[RelayWorkerThreadPool] val owner: RelayWorkerThreadPool)
            extends Thread(workerThreadGroup, target, "Relay Worker Thread-" + activeCount) {
        activeCount += 1
    }

}