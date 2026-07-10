package com.example.android.architecture.blueprints.todoapp.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.android.architecture.blueprints.todoapp.data.Result
import com.example.android.architecture.blueprints.todoapp.data.Task
import kotlinx.coroutines.runBlocking

class FakeAndroidTestRepository : TasksRepository {
    var tasksServiceData: LinkedHashMap<String, Task> = LinkedHashMap()
    private val observableTasks = MutableLiveData<Result<List<Task>>>()

    override suspend fun getTasks(forceUpdate: Boolean): Result<List<Task>> {
        return Result.Success(tasksServiceData.values.toList())
    }

    override suspend fun refreshTasks() {
        observableTasks.postValue(getTasks())
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

    override suspend fun refreshTask(taskId: String) {
        refreshTasks()
    }

    override fun observeTask(taskId: String): LiveData<Result<Task>> {
        runBlocking { refreshTasks() }
        return observableTasks.map { result ->
            when (result) {
                is Result.Loading -> Result.Loading
                is Result.Error -> Result.Error(result.exception)
                is Result.Success -> {
                    val task = result.data.firstOrNull { it.id == taskId }
                        ?: return@map Result.Error(Exception("Task not found"))
                    Result.Success(task)
                }
            }
        }
    }

    override suspend fun getTask(
        taskId: String,
        forceUpdate: Boolean
    ): Result<Task> {
        if (tasksServiceData.containsKey(taskId)) {
            return Result.Success(tasksServiceData[taskId]!!)
        } else {
            return Result.Error(Exception("Task not found"))
        }
    }

    override suspend fun saveTask(task: Task) {
        tasksServiceData[task.id] = task
        refreshTasks()
    }

    override suspend fun completeTask(task: Task) {
        tasksServiceData[task.id] = task.copy(isCompleted = true)
        refreshTasks()
    }

    override suspend fun completeTask(taskId: String) {
        tasksServiceData[taskId]?.let {
            tasksServiceData[taskId] = it.copy(isCompleted = true)
            refreshTasks()
        }
    }

    override suspend fun activateTask(task: Task) {
        tasksServiceData[task.id] = task.copy(isCompleted = false)
        refreshTasks()
    }

    override suspend fun activateTask(taskId: String) {
        tasksServiceData[taskId]?.let {
            tasksServiceData[taskId] = it.copy(isCompleted = false)
            refreshTasks()
        }
    }

    override suspend fun clearCompletedTasks() {
        tasksServiceData.values.filter { it.isCompleted }
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