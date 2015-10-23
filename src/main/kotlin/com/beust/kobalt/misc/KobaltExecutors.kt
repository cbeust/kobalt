package com.beust.kobalt.misc

import java.util.concurrent.*

class NamedThreadFactory(val n: String) : ThreadFactory {
    private val PREFIX = "K-"

    public val name: String
        get() = PREFIX + n

    override
    public fun newThread(r: Runnable) : Thread {
        val result = Thread(r)
        result.name = name + "-" + result.id
        return result
    }
}

class KobaltExecutor(name: String, threadCount: Int)
        : ThreadPoolExecutor(threadCount, threadCount, 5L, TimeUnit.SECONDS,
                LinkedBlockingQueue<Runnable>(), NamedThreadFactory(name)) {

    override protected fun afterExecute(r: Runnable, t: Throwable?) {
        super.afterExecute(r, t)
        var ex : Throwable? = null
        if (t == null && r is Future<*>) {
            try {
                if (r.isDone) r.get();
            } catch (ce: CancellationException) {
                ex = ce;
            } catch (ee: ExecutionException) {
                ex = ee.cause;
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt(); // ignore/reset
            }
        }
        if (ex != null) {
            error(if (ex.message != null) ex.message!! else ex.javaClass.toString())
        }
    }
}

public class KobaltExecutors {
    public fun newExecutor(name: String, threadCount: Int) : ExecutorService
            = KobaltExecutor(name, threadCount)

    val dependencyExecutor = newExecutor("Dependency", 5)
    val miscExecutor = newExecutor("Misc", 2)

    fun shutdown() {
        dependencyExecutor.shutdown()
        miscExecutor.shutdown()
    }

    fun <T> completionService(name: String, threadCount: Int, maxMs: Long, tasks: List<Callable<T>>,
            progress: (T) -> Unit = {}) : List<T> {
        val result = arrayListOf<T>()
        val executor = newExecutor(name, threadCount)
        val cs = ExecutorCompletionService<T>(executor)
        tasks.map { cs.submit(it) }

        var remainingMs = maxMs
        var i = 0
        while (i < tasks.size && remainingMs >= 0) {
            var start = System.currentTimeMillis()
            val r = cs.take().get(remainingMs, TimeUnit.MILLISECONDS)
            progress(r)
            result.add(r)
            remainingMs -= (System.currentTimeMillis() - start)
            log(2, "Received $r, remaining: $remainingMs ms")
            i++
        }

        if (remainingMs < 0) {
            warn("Didn't receive all the results in time: $i / ${tasks.size}")
        } else {
            log(2, "Received all results in ${maxMs - remainingMs} ms")
        }

        executor.shutdown()
        return result
    }
}
