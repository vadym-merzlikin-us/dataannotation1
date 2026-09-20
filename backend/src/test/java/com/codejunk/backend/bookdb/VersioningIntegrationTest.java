package com.codejunk.backend.bookdb;

import com.codejunk.backend.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class VersioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------- Author version field ----------

    @Test
    void newAuthorHasVersionZero() throws Exception {
        String body = mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Isaac","secondName":"Asimov","description":"Foundation"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void getAuthorReturnsVersion() throws Exception {
        String body = mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Philip","secondName":"Dick","description":"Androids"}"""))
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
                                {"firstName":"Ray","secondName":"Bradbury","description":"Fahrenheit"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/bookdb/authors").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].version").exists());
    }

    @Test
    void updatingAuthorIncrementsVersion() throws Exception {
        String body = createAuthor("Frank", "Herbert", "Dune");
        Integer id = JsonPath.read(body, "$.id");

        String updated = mockMvc.perform(put("/api/bookdb/authors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Frank","secondName":"Herbert","description":"Updated","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
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
                        .content("{\"name\":\"The Hobbit\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getBookReturnsVersion() throws Exception {
        Integer authorId = createAuthorId("Orwell", "George", "1984");

        String body = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"1984\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"Brave New World\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"Slaughterhouse-Five\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"Handmaid\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"Cryptonomicon\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"Kindred\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"The Dispossessed\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
                        .content("{\"name\":\"Hyperion\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer bookId = JsonPath.read(bookBody, "$.id");

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
                        .content("{\"name\":\"Neuromancer\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
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
            String result = mockMvc.perform(put("/api/bookdb/authors/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"Iain\",\"secondName\":\"Banks\",\"description\":\"v" + expectedVersion + "\",\"version\":" + (expectedVersion - 1) + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(expectedVersion))
                    .andReturn().getResponse().getContentAsString();
        }
    }

    // ---------- Helpers ----------

    private String createAuthor(String first, String second, String desc) throws Exception {
        return mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + first + "\",\"secondName\":\"" + second + "\",\"description\":\"" + desc + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private Integer createAuthorId(String first, String second, String desc) throws Exception {
        return JsonPath.read(createAuthor(first, second, desc), "$.id");
    }
}
