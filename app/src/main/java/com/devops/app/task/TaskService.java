package com.devops.app.task;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

/**
 * Keeps tasks in memory. Data is lost on restart, which is fine for a demo app
 * whose purpose is to be built, versioned and deployed by the pipeline.
 */
@Service
public class TaskService {

	private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
	private final AtomicLong nextId = new AtomicLong(1);

	public List<Task> findAll() {
		return tasks.values().stream().sorted(Comparator.comparingLong(Task::id)).toList();
	}

	public Task findById(long id) {
		Task task = tasks.get(id);
		if (task == null) {
			throw new TaskNotFoundException(id);
		}
		return task;
	}

	public Task create(String title) {
		long id = nextId.getAndIncrement();
		Task task = new Task(id, title.strip(), false, Instant.now());
		tasks.put(id, task);
		return task;
	}

	public Task complete(long id) {
		Task updated = tasks.computeIfPresent(id, (key, task) -> task.markDone());
		if (updated == null) {
			throw new TaskNotFoundException(id);
		}
		return updated;
	}

	public void delete(long id) {
		if (tasks.remove(id) == null) {
			throw new TaskNotFoundException(id);
		}
	}
}
