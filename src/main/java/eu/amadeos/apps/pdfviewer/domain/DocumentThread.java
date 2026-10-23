package eu.amadeos.apps.pdfviewer.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The one thread that works with the open documents, because PDFBox must not be used by two threads at once.
 * Jobs run by priority: the pages in the window first, the thumbnails and the search after them, so that
 * scrolling stays quick while a long search runs. Results are handed to the UI executor.
 */
public final class DocumentThread implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentThread.class);

    // --8<-- [start:priority]
    public enum Priority {
        /** Opening, closing, printing: before anything else. */
        DOCUMENT,
        /** Pages shown in the window. */
        VISIBLE,
        /** Text for selecting and links, of the pages in the window. */
        TEXT,
        /** Pages next to the window, rendered ahead. */
        AHEAD,
        THUMBNAIL,
        SEARCH
    }
    // --8<-- [end:priority]

    private final AtomicLong sequence = new AtomicLong();
    private final ThreadPoolExecutor executor;
    private final Executor ui;

    /** @param ui runs the results, the Swing event thread in the application */
    public DocumentThread(Executor ui) {
        this.ui = ui;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(),
                r -> Thread.ofPlatform().daemon().name("pdf-document").unstarted(r));
    }

    /**
     * Runs the job on the document thread and passes its result, or its failure, to the UI executor.
     *
     * @param onError gets the exception, also an {@link Error} such as running out of memory on a huge page
     */
    public <T> Job submit(Priority priority, Callable<T> work, Consumer<? super T> onDone,
                          Consumer<? super Throwable> onError) {
        Job job = new Job(priority, sequence.getAndIncrement()) {
            @Override
            void work() {
                if (cancelled) return;
                try {
                    T result = work.call();
                    if (!cancelled) ui.execute(() -> {
                        if (!cancelled) onDone.accept(result);
                    });
                } catch (Exception | OutOfMemoryError | StackOverflowError e) {
                    LOG.debug("Document job failed", e);
                    if (!cancelled) ui.execute(() -> onError.accept(e));
                }
            }
        };
        executor.execute(job);
        return job;
    }

    /** Drops the waiting jobs of this priority; a running one finishes, but its result is not passed on. */
    public void cancel(Priority priority) {
        executor.getQueue().removeIf(r -> r instanceof Job job && job.priority == priority && job.cancel());
    }

    /** Drops all waiting jobs. */
    public void cancelAll() {
        executor.getQueue().removeIf(r -> r instanceof Job job && job.cancel());
    }

    /** Waits until the jobs submitted so far are done; for tests. */
    public void await() throws InterruptedException {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Job marker = new Job(Priority.SEARCH, Long.MAX_VALUE) {
            @Override
            void work() {
                done.countDown();
            }

            @Override
            public boolean cancel() {
                // Cancelling searches must not drop the marker of a waiting test.
                return false;
            }
        };
        executor.execute(marker);
        if (!done.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Document thread is stuck");
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    /** A waiting or running job; {@link #cancel()} keeps its result from being passed on. */
    public abstract static class Job implements Runnable, Comparable<Job> {
        private final Priority priority;
        private final long order;
        volatile boolean cancelled;

        Job(Priority priority, long order) {
            this.priority = priority;
            this.order = order;
        }

        abstract void work();

        @Override
        public final void run() {
            work();
        }

        public boolean cancel() {
            cancelled = true;
            return true;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public Priority priority() {
            return priority;
        }

        @Override
        public int compareTo(Job other) {
            int byPriority = priority.compareTo(other.priority);
            return byPriority != 0 ? byPriority : Long.compare(order, other.order);
        }
    }
}
