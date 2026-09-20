package com.codejunk.backend.bookdb;

import com.codejunk.backend.TestcontainersConfig;
import com.codejunk.backend.bookdb.dto.AuthorRequest;
import com.codejunk.backend.bookdb.dto.BookRequest;
import com.codejunk.backend.web.VersionConflictException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Note: {@code version} is mandatory on every request body, creates included -
 * both request records declare it {@code @NotNull} and the controllers validate
 * with {@code @Valid}, so a create without it is rejected with 400 before the
 * handler runs. {@code create()} additionally requires the value to be 0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class VersioningIntegrationTest {

    /** How long a transaction waits at the rendezvous for its counterpart. */
    private static final int RENDEZVOUS_TIMEOUT_SECONDS = 2;

    /** A wedged implementation should fail the test, not hang the build. */
    private static final int TASK_TIMEOUT_SECONDS = 30;

    @Autowired
    private MockMvc mockMvc;

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

    // ---------- Author version field ----------

    @Test
    void newAuthorHasVersionZero() throws Exception {
        mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Isaac","secondName":"Asimov","description":"Foundation","version":0}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getAuthorReturnsVersion() throws Exception {
        String body = mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Philip","secondName":"Dick","description":"Androids","version":0}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/bookdb/authors/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void authorVersionInListEndpoint() throws Exception {
        mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ray","secondName":"Bradbury","description":"Fahrenheit","version":0}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/bookdb/authors").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].version").exists());
    }

    @Test
    void updatingAuthorIncrementsVersion() throws Exception {
        String body = createAuthor("Frank", "Herbert", "Dune");
        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/authors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Frank","secondName":"Herbert","description":"Updated","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updatingAuthorWithStaleVersionReturns409() throws Exception {
        String body = createAuthor("Arthur", "Clarke", "2001");
        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/authors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Arthur C.","secondName":"Clarke","description":"2001","version":0}"""))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/bookdb/authors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Arthur","secondName":"Clarke","description":"stale","version":0}"""))
                .andExpect(status().isConflict());
    }

    // ---------- Book version field ----------

    @Test
    void newBookHasVersionZero() throws Exception {
        Integer authorId = createAuthorId("Tolkien", "J.R.R.", "LOTR");

        mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"The Hobbit\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getBookReturnsVersion() throws Exception {
        Integer authorId = createAuthorId("Orwell", "George", "1984");

        String body = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"1984\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/bookdb/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void bookVersionInListEndpoint() throws Exception {
        Integer authorId = createAuthorId("Huxley", "Aldous", "BNW");

        mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Brave New World\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/bookdb/books").param("authorId", authorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].version").exists());
    }

    @Test
    void updatingBookIncrementsVersion() throws Exception {
        Integer authorId = createAuthorId("Vonnegut", "Kurt", "SF");

        String body = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Slaughterhouse-Five\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Slaughterhouse Five\",\"description\":\"edited\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updatingBookWithStaleVersionReturns409() throws Exception {
        Integer authorId = createAuthorId("Atwood", "Margaret", "Speculative");

        String body = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Handmaid\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Handmaid's Tale\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stale\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isConflict());
    }

    // ---------- Cascading version: book changes bump author version ----------

    @Test
    void creatingBookIncrementsAuthorVersion() throws Exception {
        String authorBody = createAuthor("Neal", "Stephenson", "Snow Crash");
        Integer authorId = JsonPath.read(authorBody, "$.id");
        Integer authorVersionBefore = JsonPath.read(authorBody, "$.version");

        mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cryptonomicon\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated());

        String updatedAuthor = mockMvc.perform(get("/api/bookdb/authors/" + authorId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Integer authorVersionAfter = JsonPath.read(updatedAuthor, "$.version");
        org.junit.jupiter.api.Assertions.assertTrue(authorVersionAfter > authorVersionBefore,
                "Author version should increase when a book is created");
    }

    @Test
    void updatingBookIncrementsAuthorVersion() throws Exception {
        String authorBody = createAuthor("Octavia", "Butler", "Parable");
        Integer authorId = JsonPath.read(authorBody, "$.id");

        String bookBody = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kindred\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(bookBody, "$.id");

        String authorBeforeUpdate = mockMvc.perform(get("/api/bookdb/authors/" + authorId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer authorVersionBefore = JsonPath.read(authorBeforeUpdate, "$.version");

        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kindred (Revised)\",\"description\":\"d2\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isOk());

        String authorAfterUpdate = mockMvc.perform(get("/api/bookdb/authors/" + authorId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer authorVersionAfter = JsonPath.read(authorAfterUpdate, "$.version");
        org.junit.jupiter.api.Assertions.assertTrue(authorVersionAfter > authorVersionBefore,
                "Author version should increase when a book is updated");
    }

    @Test
    void updatingAuthorDoesNotChangeBookVersion() throws Exception {
        String authorBody = createAuthor("Ursula", "LeGuin", "Earthsea");
        Integer authorId = JsonPath.read(authorBody, "$.id");

        String bookBody = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"The Dispossessed\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(bookBody, "$.id");
        Integer bookVersionBefore = JsonPath.read(bookBody, "$.version");

        String authorFresh = mockMvc.perform(get("/api/bookdb/authors/" + authorId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer currentAuthorVersion = JsonPath.read(authorFresh, "$.version");

        mockMvc.perform(put("/api/bookdb/authors/" + authorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ursula K.\",\"secondName\":\"LeGuin\",\"description\":\"Edited\",\"version\":" + currentAuthorVersion + "}"))
                .andExpect(status().isOk());

        String bookAfter = mockMvc.perform(get("/api/bookdb/books/" + bookId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer bookVersionAfter = JsonPath.read(bookAfter, "$.version");
        org.junit.jupiter.api.Assertions.assertEquals(bookVersionBefore, bookVersionAfter,
                "Book version should NOT change when its author is updated");
    }

    // ---------- Version required on update ----------

    @Test
    void updateAuthorRequiresVersionField() throws Exception {
        String body = createAuthor("China", "Mieville", "New Crobuzon");
        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/authors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"China","secondName":"Mieville","description":"No version"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBookRequiresVersionField() throws Exception {
        Integer authorId = createAuthorId("Dan", "Simmons", "Hyperion");

        String bookBody = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hyperion\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(bookBody, "$.id");

        // Deliberately no version: the update must be rejected.
        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hyperion Cantos\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Transactionality ----------

    @Test
    void failedVersionCheckDoesNotPartiallyApplyChanges() throws Exception {
        String authorBody = createAuthor("William", "Gibson", "Neuromancer");
        Integer authorId = JsonPath.read(authorBody, "$.id");

        String bookBody = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Neuromancer\",\"description\":\"d\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer bookId = JsonPath.read(bookBody, "$.id");

        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Neuromancer Edited\",\"description\":\"d2\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/bookdb/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Not Stick\",\"description\":\"d3\",\"authorId\":" + authorId + ",\"version\":0}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/bookdb/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neuromancer Edited"));
    }

    // ---------- Multiple sequential updates ----------

    @Test
    void multipleSequentialUpdatesIncrementVersionCorrectly() throws Exception {
        String body = createAuthor("Iain", "Banks", "Culture");
        Integer id = JsonPath.read(body, "$.id");

        for (int expectedVersion = 1; expectedVersion <= 3; expectedVersion++) {
            mockMvc.perform(put("/api/bookdb/authors/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"Iain\",\"secondName\":\"Banks\",\"description\":\"v" + expectedVersion + "\",\"version\":" + (expectedVersion - 1) + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(expectedVersion));
        }
    }

    // ---------- Concurrent updates ----------
    //
    // Everything above is sequential, and sequential traffic cannot tell whether
    // the version actually protects anything: read v0, save with v0, save again
    // with v0 and the second call is correctly rejected even by a check that is
    // not atomic with the write. These two drive the services directly rather
    // than through MockMvc, because MockMvc runs each request to completion and
    // there is then no way to hold a transaction open while the other one reads.
    //
    // The invariant is deliberately independent of how the code defends itself:
    // every update that reports success must be reflected in exactly one version
    // increment. Telling a client "saved" and then losing its write is the
    // failure, whatever mechanism is meant to prevent it. Pessimistic locking,
    // optimistic locking, and any other correct approach all satisfy it.

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
     * <p>Stricter than the test above: guarding only the book row still leaves
     * the author increment racy, so a partial fix will not pass.
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

    // ---------- Concurrency harness ----------

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

    // ---------- Helpers ----------

    private String createAuthor(String first, String second, String desc) throws Exception {
        return mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + first + "\",\"secondName\":\"" + second
                                + "\",\"description\":\"" + desc + "\",\"version\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private Integer createAuthorId(String first, String second, String desc) throws Exception {
        return JsonPath.read(createAuthor(first, second, desc), "$.id");
    }
}
