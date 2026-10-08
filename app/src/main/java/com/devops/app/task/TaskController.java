package com.devops.app.task;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping
	public List<Task> list() {
		return taskService.findAll();
	}

	@GetMapping("/{id}")
	public Task get(@PathVariable long id) {
		return taskService.findById(id);
	}

	@PostMapping
	public ResponseEntity<Task> create(@Valid @RequestBody CreateTaskRequest request) {
		Task task = taskService.create(request.title());
		return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
	}

	@PatchMapping("/{id}/complete")
	public Task complete(@PathVariable long id) {
		return taskService.complete(id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) {
		taskService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
