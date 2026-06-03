/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.architecture.blueprints.todoapp.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.android.architecture.blueprints.todoapp.data.Result
import com.example.android.architecture.blueprints.todoapp.data.Result.Error
import com.example.android.architecture.blueprints.todoapp.data.Result.Success
import com.example.android.architecture.blueprints.todoapp.data.Task

/**
 * Fake implementation of [TasksDataSource] for testing purposes.
 * Uses in-memory storage to simulate a data source without network or database dependencies.
 */
class FakeDataSource(private var initialTasks: List<Task> = emptyList()) : TasksDataSource {

    private var tasksData = LinkedHashMap<String, Task>()

    private val observableTasks = MutableLiveData<Result<List<Task>>>()

    init {
        // Add initial tasks if provided
        initialTasks.forEach { task ->
            tasksData[task.id] = task
        }
        
        // Add some default tasks for testing if no initial tasks provided
        if (initialTasks.isEmpty()) {
            addTask("Build tower in Pisa", "Ground looks good, no foundation work required.")
            addTask("Finish bridge in Tacoma", "Found awesome girders at half the cost!")
        }
    }

    private fun addTask(title: String, description: String) {
        val newTask = Task(title, description)
        tasksData[newTask.id] = newTask
    }

    override fun observeTasks(): LiveData<Result<List<Task>>> {
        return observableTasks
    }

    override suspend fun getTasks(): Result<List<Task>> {
        return Success(tasksData.values.toList())
    }

    override suspend fun refreshTasks() {
        observableTasks.value = getTasks()
    }

    override fun observeTask(taskId: String): LiveData<Result<Task>> {
        return observableTasks.map { tasks ->
            when (tasks) {
                is Result.Loading -> Result.Loading
                is Error -> Error(tasks.exception)
                is Success -> {
                    val task = tasks.data.firstOrNull { it.id == taskId }
                        ?: return@map Error(Exception("Task not found"))
                    Success(task)
                }
            }
        }
    }

    override suspend fun getTask(taskId: String): Result<Task> {
        tasksData[taskId]?.let {
            return Success(it)
        }
        return Error(Exception("Task not found"))
    }

    override suspend fun refreshTask(taskId: String) {
        refreshTasks()
    }

    override suspend fun saveTask(task: Task) {
        tasksData[task.id] = task
        refreshTasks()
    }

    override suspend fun completeTask(task: Task) {
        val completedTask = Task(task.title, task.description, true, task.id)
        tasksData[task.id] = completedTask
        refreshTasks()
    }

    override suspend fun completeTask(taskId: String) {
        tasksData[taskId]?.let { task ->
            completeTask(task)
        }
    }

    override suspend fun activateTask(task: Task) {
        val activeTask = Task(task.title, task.description, false, task.id)
        tasksData[task.id] = activeTask
        refreshTasks()
    }

    override suspend fun activateTask(taskId: String) {
        tasksData[taskId]?.let { task ->
            activateTask(task)
        }
    }

    override suspend fun clearCompletedTasks() {
        tasksData = tasksData.filterValues { !it.isCompleted } as LinkedHashMap<String, Task>
        refreshTasks()
    }

    override suspend fun deleteAllTasks() {
        tasksData.clear()
        refreshTasks()
    }

    override suspend fun deleteTask(taskId: String) {
        tasksData.remove(taskId)
        refreshTasks()
    }

    // Helper methods for testing
    suspend fun addTasks(tasks: List<Task>) {
        tasks.forEach { task ->
            tasksData[task.id] = task
        }
        refreshTasks()
    }

    suspend fun setTasksData(tasks: List<Task>) {
        tasksData.clear()
        initialTasks = tasks
        addTasks(tasks)
    }

    fun getTasksData(): List<Task> {
        return tasksData.values.toList()
    }
}
