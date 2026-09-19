package com.codejunk.backend.bookdb;

import com.codejunk.backend.TestcontainersConfig;
import com.codejunk.backend.bookdb.dto.AuthorRequest;
import com.codejunk.backend.bookdb.dto.BookRequest;
import com.codejunk.backend.web.VersionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two users editing the catalogue at the same time.
 *
 * <p>The invariant under test is deliberately independent of how the code
 * defends itself: <em>every update that reports success must be reflected in
 * exactly one version increment</em>. Telling a client "saved" and then losing
 * its write is the failure, whatever mechanism is supposed to prevent it.
 *
 * <p>So a pessimistic implementation (the second transaction blocks, re-reads,
 * and rejects), an optimistic one (the conditional update matches no row and
 * rejects), and any other correct approach all satisfy it. Only a check that is
 * not atomic with the write fails.
 *
 * <p>These cannot be expressed sequentially. Read v0, save with v0, save again
 * with v0 and the second call is correctly rejected — the comparison itself is
 * sound. Only genuine overlap exposes the gap between checking and writing.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ConcurrentUpdateTest {

    /**
     * How long a transaction waits at the rendezvous for its counterpart.
     * Reaching the timeout is not a failure: it means the other transaction is
     * held behind a lock and could not get there, which is what a correct
     * implementation does.
     */
    private static final int RENDEZVOUS_TIMEOUT_SECONDS = 2;

    /** A wedged implementation should fail the test, not hang the build. */
    private static final int TASK_TIMEOUT_SECONDS = 30;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private BookService bookService;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentEditsOfOneAuthorMustNotSilentlyDropOne() throws Exception {
        Long authorId = newAuthor();
        long startVersion = authorVersion(authorId);

        CyclicBarrier bothHaveRead = new CyclicBarrier(2);
        List<Outcome> outcomes = runConcurrently(
                editing(bothHaveRead, () -> authorService.update(authorId,
                        new AuthorRequest("Ursula", "Le Guin", "edited by A", startVersion))),
                editing(bothHaveRead, () -> authorService.update(authorId,
                        new AuthorRequest("Isaac", "Asimov", "edited by B", startVersion))));

        assertOnlyConflictFailures(outcomes);
        long successes = successes(outcomes);

        assertThat(successes)
                .as("at least one of two edits should get through")
                .isGreaterThanOrEqualTo(1);

        assertThat(authorVersion(authorId))
                .as("%d edit(s) reported success, so the version must have advanced by %d",
                        successes, successes)
                .isEqualTo(startVersion + successes);
    }

    /**
     * The same flaw without a conflict to detect. Two <em>different</em> books
     * are edited, so each book's own version check passes legitimately — but
     * {@code BookService.update} also increments the shared author, and both
     * transactions read that author before either commits.
     *
     * <p>Both edits are valid, both report success, and one author increment
     * disappears with nothing raised anywhere.
     *
     * <p>Note this is stricter than the first test: guarding only the book row
     * still leaves the author increment racy, so a partial fix will not pass.
     */
    @Test
    void concurrentEditsOfTwoBooksMustNotLoseTheirAuthorIncrement() throws Exception {
        Long authorId = newAuthor();
        Long firstBookId = newBook(authorId, "Left hand");
        Long secondBookId = newBook(authorId, "Right hand");

        long startAuthorVersion = authorVersion(authorId);
        long firstBookVersion = bookVersion(firstBookId);
        long secondBookVersion = bookVersion(secondBookId);

        CyclicBarrier bothHaveRead = new CyclicBarrier(2);
        List<Outcome> outcomes = runConcurrently(
                editing(bothHaveRead, () -> bookService.update(firstBookId,
                        new BookRequest("Left hand, revised", "edited by A", authorId, firstBookVersion))),
                editing(bothHaveRead, () -> bookService.update(secondBookId,
                        new BookRequest("Right hand, revised", "edited by B", authorId, secondBookVersion))));

        assertOnlyConflictFailures(outcomes);
        long successes = successes(outcomes);

        assertThat(successes)
                .as("the two books are unrelated, so at least one edit should get through")
                .isGreaterThanOrEqualTo(1);

        assertThat(authorVersion(authorId))
                .as("%d book edit(s) reported success, and each bumps the author, "
                        + "so the author version must have advanced by %d", successes, successes)
                .isEqualTo(startAuthorVersion + successes);
    }

    // ---- harness ------------------------------------------------------------

    /**
     * Runs one edit in its own transaction, then holds that transaction open at
     * the rendezvous so the other one reads the same starting state. The commit
     * — and therefore the UPDATE — happens only once both have arrived, or once
     * the wait times out.
     */
    private Callable<Outcome> editing(CyclicBarrier rendezvous, Runnable edit) {
        return () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            try {
                transaction.execute(status -> {
                    edit.run();
                    waitFor(rendezvous);
                    return null;
                });
                return Outcome.succeeded();
            } catch (Exception ex) {
                return Outcome.failed(ex);
            }
        };
    }

    private static void waitFor(CyclicBarrier rendezvous) {
        try {
            rendezvous.await(RENDEZVOUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException | BrokenBarrierException ex) {
            // The other transaction never got here, so it is waiting on a lock we
            // hold. Correct behaviour - stop waiting and let it through.
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Outcome> runConcurrently(Callable<Outcome> first, Callable<Outcome> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> running = List.of(pool.submit(first), pool.submit(second));
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : running) {
                outcomes.add(future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(boolean success, Throwable error) {

        static Outcome succeeded() {
            return new Outcome(true, null);
        }

        static Outcome failed(Throwable error) {
            return new Outcome(false, error);
        }
    }

    private static long successes(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::success).count();
    }

    /**
     * Losing to a conflict is a legitimate way to refuse an edit. Anything else
     * is a bug in the implementation, and would otherwise let the invariant pass
     * for the wrong reason.
     */
    private static void assertOnlyConflictFailures(List<Outcome> outcomes) {
        for (Outcome outcome : outcomes) {
            if (!outcome.success() && !isConflict(outcome.error())) {
                throw new AssertionError(
                        "an edit failed for a reason other than a version conflict", outcome.error());
            }
        }
    }

    private static boolean isConflict(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof VersionConflictException
                    || cause instanceof OptimisticLockingFailureException
                    || cause instanceof PessimisticLockingFailureException
                    || cause instanceof CannotAcquireLockException) {
                return true;
            }
        }
        return false;
    }

    // ---- fixtures -----------------------------------------------------------

    private Long newAuthor() {
        return new TransactionTemplate(transactionManager).execute(status ->
                authorRepository.save(new Author("Concurrent", "Fixture", null)).getId());
    }

    private Long newBook(Long authorId, String name) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Author author = authorRepository.findById(authorId).orElseThrow();
            return bookRepository.save(new Book(name, null, author)).getId();
        });
    }

    private long authorVersion(Long id) {
        return authorRepository.findById(id).orElseThrow().getVersion();
    }

    private long bookVersion(Long id) {
        return bookRepository.findById(id).orElseThrow().getVersion();
    }
}
