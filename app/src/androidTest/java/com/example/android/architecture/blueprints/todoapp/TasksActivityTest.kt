package com.example.android.architecture.blueprints.todoapp

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import com.example.android.architecture.blueprints.todoapp.tasks.TasksActivity
import com.example.android.architecture.blueprints.todoapp.util.DataBindingIdlingResource
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource
import com.example.android.architecture.blueprints.todoapp.util.monitorActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TasksActivityTest {
    private lateinit var repository: TasksRepository
    private val dataBindingIdlingResource = DataBindingIdlingResource()

    @Before
    fun init() {
        repository = ServiceLocator.provideTasksRepository(getApplicationContext())
        runBlocking {
            repository.deleteAllTasks()
        }
    }

    @After
    fun reset() {
        ServiceLocator.resetRepository()
    }

    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance()
            .register(EspressoIdlingResource.countingIdlingResource, dataBindingIdlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance()
            .unregister(EspressoIdlingResource.countingIdlingResource, dataBindingIdlingResource)
    }

    @Test
    fun editTask() = runBlocking {
        // Set initial state
        repository.saveTask(Task("Title 1", "Description 1"))

        // Start up Tasks screen
        val activityScenario = ActivityScenario.launch(TasksActivity::class.java)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // Click on the first task on the list and verify that all the data is correct
        onView(withText("Title 1")).perform(click())
        onView(withId(R.id.task_detail_title_text)).check(matches(withText("Title 1")))
        onView(withId(R.id.task_detail_description_text)).check(matches(withText("Description 1")))
        onView(withId(R.id.task_detail_complete_checkbox)).check(matches(not(isChecked())))

        // Click on the edit button, edit, and save
        onView(withId(R.id.edit_task_fab)).perform(click())
        onView(withId(R.id.add_task_title_edit_text)).perform(replaceText("New Title 1"))
        onView(withId(R.id.add_task_description_edit_text)).perform(replaceText("New description 1"))
        onView(withId(R.id.save_task_fab)).perform(click())

        // Verify task is displayed on screen in the task list
        onView(withText("New Title 1")).check(matches(isDisplayed()))
        onView(withText("Title 1")).check(doesNotExist())

        // Make sure the activity is closed before resetting the db:
        activityScenario.close()
    }

    @Test
    fun createOneTask_deleteTask() {
        val activityScenario = ActivityScenario.launch(TasksActivity::class.java)
        dataBindingIdlingResource.monitorActivity(activityScenario)

        // Add an active task by clicking on the add task button
        onView(withId(R.id.add_task_fab)).perform(click())
        onView(withId(R.id.add_task_title_edit_text)).perform(replaceText("Publish Moveflix app on the Google Play"))
        onView(withId(R.id.add_task_description_edit_text)).perform(replaceText("Moveflix is a Flutter app to watch workouts"))
        onView(withId(R.id.save_task_fab)).perform(click())

        // Verify task is displayed on screen in the task list
        onView(withText("Publish Moveflix app on the Google Play")).check(matches(isDisplayed()))

        // Open the task detail
        onView(withText("Publish Moveflix app on the Google Play")).perform(click())
        onView(withId(R.id.task_detail_title_text)).check(matches(withText("Publish Moveflix app on the Google Play")))
        onView(withId(R.id.task_detail_description_text)).check(matches(withText("Moveflix is a Flutter app to watch workouts")))
        onView(withId(R.id.task_detail_complete_checkbox)).check(matches(not(isChecked())))

        // Click on the delete button
        onView(withId(R.id.menu_delete)).perform(click())

        // Verify task is removed from the list
        onView(withText("Publish Moveflix app on the Google Play")).check(doesNotExist())
    }
}