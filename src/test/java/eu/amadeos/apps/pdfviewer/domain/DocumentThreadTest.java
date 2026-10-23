package eu.amadeos.apps.pdfviewer.domain;

import eu.amadeos.apps.pdfviewer.domain.DocumentThread.Priority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentThreadTest {
    @Test
    void visiblePagesBeforeSearchAndCancelledJobsDropped() throws Exception {
        List<String> done = new CopyOnWriteArrayList<>();
        try (DocumentThread thread = new DocumentThread(Runnable::run)) {
            CountDownLatch blocker = new CountDownLatch(1);
            thread.submit(Priority.DOCUMENT, () -> blocker.await(5, TimeUnit.SECONDS), r -> done.add("first"), e -> {
            });
            thread.submit(Priority.SEARCH, () -> "search", done::add, e -> {
            });
            thread.submit(Priority.THUMBNAIL, () -> "thumbnail", done::add, e -> {
            });
            thread.submit(Priority.VISIBLE, () -> "visible", done::add, e -> {
            });
            thread.submit(Priority.AHEAD, () -> "ahead", done::add, e -> {
            });
            thread.cancel(Priority.THUMBNAIL);
            blocker.countDown();
            thread.await();
            assertEquals(List.of("first", "visible", "ahead", "search"), done);
        }
    }

    @Test
    void failuresReachTheErrorHandler() throws Exception {
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        try (DocumentThread thread = new DocumentThread(Runnable::run)) {
            thread.submit(Priority.VISIBLE, () -> {
                throw new IllegalStateException("broken page");
            }, r -> {
            }, errors::add);
            thread.await();
        }
        assertTrue(errors.getFirst() instanceof IllegalStateException);
    }
}
