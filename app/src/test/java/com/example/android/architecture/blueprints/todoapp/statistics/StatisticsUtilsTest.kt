package com.example.android.architecture.blueprints.todoapp.statistics

import com.example.android.architecture.blueprints.todoapp.data.Task
import org.junit.Assert.*
import org.junit.Test

class StatisticsUtilsTest {

    @Test
    fun getActiveAndCompletedStats_empty_returnsZero() {
        val emptyTasks = emptyList<Task>()

        val result = getActiveAndCompletedStats(emptyTasks)

        assertEquals(0f, result.completedTasksPercent)
        assertEquals(0f, result.activeTasksPercent)
    }

    @Test
    fun getActiveAndCompletedStats_null_returnsZero() {
        val nullTasks = null

        val result = getActiveAndCompletedStats(nullTasks)

        assertEquals(0f, result.completedTasksPercent)
        assertEquals(0f, result.activeTasksPercent)
    }

    // If there's no completed task and one active task,
    // then there are 100% percent active tasks and 0% completed tasks.
    @Test
    fun getActiveAndCompletedStats_noCompleted_returnsZeroHundred() {
        val tasks = listOf<Task>(
            Task("title", "description", false),
            Task("title", "description", false)
        )

        val result = getActiveAndCompletedStats(tasks)
        assertEquals(0f, result.completedTasksPercent)
        assertEquals(100f, result.activeTasksPercent)
    }

    // If there's 2 completed tasks and 3 active tasks,
    // then there are 60% percent active tasks and 40% completed tasks.
    @Test
    fun getActiveAndCompletedStats_twoCompletedThreeActive_returnsFortySixty() {
        val tasks = listOf(
            Task("Completed task 1", "Completed task 1 description", true),
            Task("Completed task 2", "Completed task 2 description", true),
            Task("Active task 1", "Active task 1 description", false),
            Task("Active task 2", "Active task 2 description", false),
            Task("Active task 3", "Active task 3 description", false)
        )

        val result = getActiveAndCompletedStats(tasks)
        assertEquals(40f, result.completedTasksPercent)
        assertEquals(60f, result.activeTasksPercent)
    }
}