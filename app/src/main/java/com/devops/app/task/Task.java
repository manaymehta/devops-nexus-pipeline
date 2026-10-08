package com.devops.app.task;

import java.time.Instant;

public record Task(long id, String title, boolean done, Instant createdAt) {

	Task markDone() {
		return new Task(id, title, true, createdAt);
	}
}
