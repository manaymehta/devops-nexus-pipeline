package com.devops.app.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

	private TaskService service;

	@BeforeEach
	void setUp() {
		service = new TaskService();
	}

	@Test
	void createAssignsIncrementingIdsAndTrimsTitle() {
		Task first = service.create("  Write Dockerfile  ");
		Task second = service.create("Configure Nexus");

		assertThat(first.id()).isEqualTo(1);
		assertThat(first.title()).isEqualTo("Write Dockerfile");
		assertThat(first.done()).isFalse();
		assertThat(second.id()).isEqualTo(2);
	}

	@Test
	void findAllReturnsTasksInIdOrder() {
		service.create("a");
		service.create("b");
		service.create("c");

		assertThat(service.findAll()).extracting(Task::title).containsExactly("a", "b", "c");
	}

	@Test
	void completeMarksTaskDone() {
		Task task = service.create("Run pipeline");

		Task completed = service.complete(task.id());

		assertThat(completed.done()).isTrue();
		assertThat(service.findById(task.id()).done()).isTrue();
	}

	@Test
	void deleteRemovesTask() {
		Task task = service.create("Temporary");

		service.delete(task.id());

		assertThat(service.findAll()).isEmpty();
	}

	@Test
	void unknownIdThrowsNotFound() {
		assertThatThrownBy(() -> service.findById(99)).isInstanceOf(TaskNotFoundException.class);
		assertThatThrownBy(() -> service.complete(99)).isInstanceOf(TaskNotFoundException.class);
		assertThatThrownBy(() -> service.delete(99)).isInstanceOf(TaskNotFoundException.class);
	}
}
