package backend.utils

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.parallel.{ForkJoinTaskSupport, ForkJoinTasks, Task}
import scala.concurrent.forkjoin._

object WorkaroundTaskSupport {

  class MyForkJoinTaskSupport(
    environment0: ForkJoinPool = ForkJoinTasks.defaultForkJoinPool)
      extends ForkJoinTaskSupport(environment0) {
    override def execute[R, Tp](task: Task[R, Tp]): () => R = {
      val fjtask = newWrappedTask(task)

      Thread.currentThread match {
        case fjw: ForkJoinWorkerThread if fjw.getPool eq forkJoinPool => fjtask.fork()
        case _                                                        => forkJoinPool.execute(fjtask)
      }
      () =>
        {
          fjtask.sync()
          fjtask.body.forwardThrowable()
          fjtask.body.result
        }
    }

    override def executeAndWaitResult[R, Tp](task: Task[R, Tp]): R = {
      val fjtask = newWrappedTask(task)

      Thread.currentThread match {
        case fjw: ForkJoinWorkerThread if fjw.getPool eq forkJoinPool => fjtask.fork()
        case _                                                        => forkJoinPool.execute(fjtask)
      }
      fjtask.sync()
      fjtask.body.forwardThrowable()
      fjtask.body.result
    }
  }

  object DefaultThreadFactory {
    private val poolNumber = new AtomicInteger(1)
  }

  class DefaultThreadFactory() extends ThreadFactory {

    val s: SecurityManager = System.getSecurityManager

    val group: ThreadGroup =
      if (s != null) s.getThreadGroup
      else Thread.currentThread.getThreadGroup

    val namePrefix
      : String = "pool-" + DefaultThreadFactory.poolNumber.getAndIncrement + "-thread-"

    val threadNumber = new AtomicInteger(1)

    override def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement, 0)
      if (t.isDaemon) t.setDaemon(false)
      if (t.getPriority != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
      t
    }
  }
}
