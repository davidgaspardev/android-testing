package com.example.android.architecture.blueprints.todoapp.statistics

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.FakeTestRepository
import com.example.android.architecture.blueprints.todoapp.util.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class StatisticsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var statisticsViewModel: StatisticsViewModel

    private lateinit var tasksRepository: FakeTestRepository

    @Before
    fun setupStatisticsViewModel() {
        tasksRepository = FakeTestRepository()

        statisticsViewModel = StatisticsViewModel(tasksRepository)
    }

    @Test
    fun activeTasksPercent_66_67percent() = mainCoroutineRule.runBlockingTest {
        val activeTask1 = Task("Task 1", "Description 1", false)
        val activeTask2 = Task("Task 2", "Description 2", false)
        val completedTask3 = Task("Task 3", "Description 3", true)
        tasksRepository.addTasks(activeTask1)
        tasksRepository.addTasks(activeTask2)
        tasksRepository.addTasks(completedTask3)

        statisticsViewModel.refresh()

        assertEquals(66.67f, statisticsViewModel.activeTasksPercent.getOrAwaitValue(), 0.01f)
        assertEquals(33.33f, statisticsViewModel.completedTasksPercent.getOrAwaitValue(), 0.01f)
    }
}