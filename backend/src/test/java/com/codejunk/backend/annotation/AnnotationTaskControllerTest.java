package com.codejunk.backend.annotation;

import com.codejunk.backend.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AnnotationTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsSeededTasks() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void createsLabelsAndDeletesATask() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"Shipping was quick\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.text").value("Shipping was quick"))
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        mockMvc.perform(put("/api/tasks/" + id + "/label")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"positive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("positive"))
                .andExpect(jsonPath("$.labelledAt").exists());

        mockMvc.perform(delete("/api/tasks/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/tasks/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankText() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
