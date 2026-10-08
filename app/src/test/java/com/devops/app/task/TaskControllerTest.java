package com.devops.app.task;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the HTTP layer: URLs, status codes, JSON shape and input validation. */
@WebMvcTest(TaskController.class)
@Import(TaskService.class)
class TaskControllerTest {

	@Autowired
	private MockMvc mvc;

	/** Creates a task and returns its URL. The app context is shared between tests, so ids are not fixed. */
	private String createTask(String title) throws Exception {
		return mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + title + "\"}"))
			.andReturn()
			.getResponse()
			.getHeader("Location");
	}

	@Test
	void createReturns201WithLocation() throws Exception {
		mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Build jar\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", matchesPattern("/api/tasks/\\d+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("Build jar"))
			.andExpect(jsonPath("$.done").value(false));
	}

	@Test
	void blankTitleIsRejected() throws Exception {
		mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listIncludesCreatedTasks() throws Exception {
		createTask("list-one");
		createTask("list-two");

		mvc.perform(get("/api/tasks"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].title", hasItems("list-one", "list-two")));
	}

	@Test
	void completeMarksTaskDone() throws Exception {
		String url = createTask("Push image");

		mvc.perform(patch(url + "/complete"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.done").value(true));
	}

	@Test
	void deleteReturns204ThenTaskIsGone() throws Exception {
		String url = createTask("Old task");

		mvc.perform(delete(url)).andExpect(status().isNoContent());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void unknownTaskReturns404() throws Exception {
		mvc.perform(get("/api/tasks/999999")).andExpect(status().isNotFound());
		mvc.perform(patch("/api/tasks/999999/complete")).andExpect(status().isNotFound());
	}
}
