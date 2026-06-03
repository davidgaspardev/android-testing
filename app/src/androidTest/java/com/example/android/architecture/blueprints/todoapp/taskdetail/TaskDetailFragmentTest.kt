package com.example.android.architecture.blueprints.todoapp.taskdetail

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.ServiceLocator
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.FakeAndroidTestRepository
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class TaskDetailFragmentTest {

    private lateinit var repository: TasksRepository

    @Before
    fun setupRepository() {
        repository = FakeAndroidTestRepository()
        ServiceLocator.tasksRepository = repository
    }

    @Test
    fun activeTaskDetails_DisplayedInUi() {
        val activeTask = Task("Active Task", "AndroidX Rocks", false)

        val bundle = TaskDetailFragmentArgs(activeTask.id).toBundle()
        launchFragmentInContainer<TaskDetailFragment>(bundle, R.style.AppTheme)
    }
}