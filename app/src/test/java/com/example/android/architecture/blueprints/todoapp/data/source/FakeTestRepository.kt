package com.example.android.architecture.blueprints.todoapp.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.android.architecture.blueprints.todoapp.data.Result
import com.example.android.architecture.blueprints.todoapp.data.Result.Error
import com.example.android.architecture.blueprints.todoapp.data.Result.Success
import com.example.android.architecture.blueprints.todoapp.data.Task
import kotlinx.coroutines.runBlocking

class FakeTestRepository : TasksRepository {

    private var shouldReturnError = false

    var tasksServiceData: LinkedHashMap<String, Task> = LinkedHashMap()
    private val observableTasks = MutableLiveData<Result<List<Task>>>()

    fun setReturnError(value: Boolean) {
        shouldReturnError = value
    }

    override suspend fun getTasks(forceUpdate: Boolean): Result<List<Task>> {
        if (shouldReturnError) {
            return Error(Exception("Error"))
        }
        return Success(tasksServiceData.values.toList())
    }

    override suspend fun refreshTasks() {
        observableTasks.value = getTasks()
    }

    override fun observeTasks(): LiveData<Result<List<Task>>> {
        runBlocking { refreshTasks() }
        return observableTasks
    }

    fun addTasks(vararg tasks: Task) {
        for (task in tasks) {
            tasksServiceData[task.id] = task
        }
        runBlocking { refreshTasks() }
    }

    override suspend fun getTask(
        taskId: String,
        forceUpdate: Boolean
    ): Result<Task> {
        if (shouldReturnError) {
            return Error(Exception("Error"))
        }
        tasksServiceData[taskId]?.let {
            return Success(it)
        }
        return Error(Exception("Could not find task with id $taskId"))
    }

    override suspend fun refreshTask(taskId: String) {
        refreshTasks()
    }

    override fun observeTask(taskId: String): LiveData<Result<Task>> {
        runBlocking { refreshTasks() }
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

    override suspend fun saveTask(task: Task) {
        tasksServiceData[task.id] = task
        refreshTasks()
    }

    override suspend fun completeTask(task: Task) {
        tasksServiceData[task.id]?.isCompleted = true
        refreshTasks()
    }

    override suspend fun completeTask(taskId: String) {
        tasksServiceData[taskId]?.isCompleted = true
        refreshTasks()
    }

    override suspend fun activateTask(task: Task) {
        tasksServiceData[task.id]?.isCompleted = false
        refreshTasks()
    }

    override suspend fun activateTask(taskId: String) {
        tasksServiceData[taskId]?.isCompleted = false
        refreshTasks()
    }

    override suspend fun clearCompletedTasks() {
        tasksServiceData = tasksServiceData.filterValues { !it.isCompleted } as LinkedHashMap<String, Task>
        refreshTasks()
    }

    override suspend fun deleteAllTasks() {
        tasksServiceData.clear()
        refreshTasks()
    }

    override suspend fun deleteTask(taskId: String) {
        tasksServiceData.remove(taskId)
        refreshTasks()
    }
}