package com.devops.app;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Starts the whole application and checks the endpoints the pipeline relies on. */
@SpringBootTest
@AutoConfigureMockMvc
class DevopsAppApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void healthEndpointIsUp() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void versionEndpointReportsBuildVersion() throws Exception {
		mvc.perform(get("/api/version"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("devops-app"))
			.andExpect(jsonPath("$.version").value(not("unknown")));
	}
}
