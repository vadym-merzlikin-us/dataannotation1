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

import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class BookDbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authorsPageDefaultsToTwenty() throws Exception {
        mockMvc.perform(get("/api/bookdb/authors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(25)))
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void authorsSecondPageReturnsTheRemainder() throws Exception {
        mockMvc.perform(get("/api/bookdb/authors").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void rejectsAnOutOfRangePageSize() throws Exception {
        mockMvc.perform(get("/api/bookdb/authors").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void booksArePagedPerAuthor() throws Exception {
        Long authorId = firstAuthorId();

        mockMvc.perform(get("/api/bookdb/books").param("authorId", authorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(26)));
    }

    @Test
    void createsAndUpdatesAnAuthor() throws Exception {
        String body = mockMvc.perform(post("/api/bookdb/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ursula","secondName":"Le Guin","description":"Added by test"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ursula"))
                .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/authors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ursula K.","secondName":"Le Guin","description":"Edited"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ursula K."))
                .andExpect(jsonPath("$.description").value("Edited"));
    }

    @Test
    void createsAndUpdatesABook() throws Exception {
        Long authorId = firstAuthorId();

        String body = mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A Wizard of Earthsea\",\"description\":\"d\",\"authorId\":" + authorId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(authorId))
                .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/bookdb/books/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"The Tombs of Atuan\",\"description\":\"d2\",\"authorId\":" + authorId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("The Tombs of Atuan"));
    }

    @Test
    void rejectsABookWithoutAName() throws Exception {
        mockMvc.perform(post("/api/bookdb/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"authorId\":" + firstAuthorId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAMissingAuthor() throws Exception {
        mockMvc.perform(get("/api/bookdb/authors/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicApiReturnsEveryBookForAnAuthor() throws Exception {
        mockMvc.perform(get("/api/public/books").param("authorId", firstAuthorId().toString()))
                .andExpect(status().isOk())
                // Unpaged, unlike the /api/bookdb listing.
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(26)));
    }

    /** The seeded author carrying 26 books is the one with the lowest id. */
    private Long firstAuthorId() throws Exception {
        String body = mockMvc.perform(get("/api/bookdb/authors").param("size", "200"))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.content[*].id");
        return ids.stream().min(Integer::compareTo).orElseThrow().longValue();
    }
}
