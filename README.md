# TO-DO Notes — Android Testing Codelab

A study project covering the full Android testing stack, from pure unit tests to instrumented UI tests on a real device.

Based on the [Advanced Android Kotlin Testing Codelabs](https://codelabs.developers.google.com/codelabs/advanced-android-kotlin-training-testing-basics) (5.1–5.3) by Google.

![App main screen, screenshot](screenshot.png)

---

## Table of Contents

1. [Project overview](#1-project-overview)
2. [The test pyramid](#2-the-test-pyramid)
3. [Unit tests — `src/test`](#3-unit-tests--srctest)
4. [Instrumented tests — `src/androidTest`](#4-instrumented-tests--srcandroidtest)
5. [Test doubles — Fakes](#5-test-doubles--fakes)
6. [Testing LiveData](#6-testing-livedata)
7. [Testing coroutines](#7-testing-coroutines)
8. [Testing Fragments with Espresso](#8-testing-fragments-with-espresso)
9. [Testing Room — DAO and LocalDataSource](#9-testing-room--dao-and-localdatasource)
10. [Dependency injection in tests — Service Locator](#10-dependency-injection-in-tests--service-locator)
11. [Key utilities](#11-key-utilities)
12. [Running the tests](#12-running-the-tests)
13. [Pre-requisites](#13-pre-requisites)

---

## 1. Project overview

TO-DO Notes is a task management app built with:

- **Architecture**: MVVM + Repository pattern
- **UI**: Fragments + Data Binding + Navigation Component
- **Async**: Kotlin Coroutines + Flow
- **Persistence**: Room
- **Observability**: LiveData

The architecture layers map directly to what we test at each level:

```
UI (Fragments / Activities)   ←  Instrumented tests (Espresso)
        ↕
  ViewModels                  ←  Unit tests (JVM)
        ↕
  Repository                  ←  Unit tests (JVM)
        ↕
DataSources (Room / Network)  ←  Instrumented tests (Room in-memory DB)
```

---

## 2. The test pyramid

Android testing is organized in three layers. The rule of thumb is: **more unit tests, fewer UI tests**.

```
        /\
       /  \        UI / Instrumented tests
      /    \       → slow, test real device behavior
     /──────\
    /        \     Integration tests
   /          \    → test how layers work together
  /────────────\
 /              \  Unit tests
/                \ → fast, isolated, run on the JVM
──────────────────
```

| Layer | Location | Speed | Isolation | Examples in this project |
|---|---|---|---|---|
| Unit | `src/test` | Fast (ms) | High | `TasksViewModelTest`, `StatisticsViewModelTest`, `DefaultTasksRepositoryTest` |
| Integration | `src/test` or `src/androidTest` | Medium | Medium | `TasksDaoTest`, `TasksLocalDataSourceTest` |
| UI / E2E | `src/androidTest` | Slow (s) | Low | `TasksFragmentTest`, `TaskDetailFragmentTest` |

---

## 3. Unit tests — `src/test`

Unit tests run on the **JVM**, without an Android device or emulator. They are fast and should make up the bulk of your test suite.

### StatisticsUtilsTest — pure logic

The simplest kind of test: a pure function with no Android dependencies.

```kotlin
@Test
fun getActiveAndCompletedStats_noCompleted_returnsZeroHundred() {
    val tasks = listOf(
        Task("title", "description", false),
        Task("title", "description", false)
    )
    val result = getActiveAndCompletedStats(tasks)
    assertEquals(0f, result.completedTasksPercent)
    assertEquals(100f, result.activeTasksPercent)
}
```

No rules, no setup — just call the function and assert.

### TasksViewModelTest — ViewModel with LiveData and coroutines

ViewModels involve LiveData and coroutines, both of which need special handling in tests.

```kotlin
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class TasksViewModelTest {

    @get:Rule val mainCoroutineRule = MainCoroutineRule()  // controls coroutine dispatcher
    @get:Rule var instantExecutorRule = InstantTaskExecutorRule()  // controls LiveData

    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var tasksRepository: FakeTestRepository

    @Before
    fun setupViewModel() {
        tasksRepository = FakeTestRepository()
        tasksViewModel = TasksViewModel(tasksRepository)
    }

    @Test
    fun addNewTask_setsNewTaskEvent() {
        tasksViewModel.addNewTask()
        val value = tasksViewModel.newTaskEvent.getOrAwaitValue()
        assertNotNull(value.getContentIfNotHandled())
    }

    @Test
    fun completeTask_dataAndSnackbarUpdated() = runTest(mainCoroutineRule.dispatcher) {
        val task = Task("Title", "Description")
        tasksRepository.addTasks(task)

        tasksViewModel.completeTask(task, true)
        advanceUntilIdle()  // run pending coroutines

        assertThat(tasksRepository.tasksServiceData[task.id]?.isCompleted, `is`(true))
    }
}
```

### DefaultTasksRepositoryTest — Repository layer

Tests that the repository correctly delegates to the remote data source when `forceUpdate = true`.

```kotlin
@Test
fun getTasks_requestsAllTasksFromRemoteDataSource() = runTest(mainCoroutineRule.dispatcher) {
    val tasks = tasksRepository.getTasks(true) as Result.Success
    assertThat(tasks.data, IsEqual(remoteTasks))
}
```

---

## 4. Instrumented tests — `src/androidTest`

Instrumented tests run **on a real device or emulator**. They have access to the Android framework, so they can test UI interactions, databases, and system services.

They are slower, but necessary for validating behavior that can't be faked on the JVM.

---

## 5. Test doubles — Fakes

A **test double** is a replacement for a real dependency used during testing. The most common in this project is the **Fake** — a lightweight in-memory implementation of a real interface.

### Why use fakes?

- The real `DefaultTasksRepository` talks to Room and a network layer. Testing it directly makes tests slow and flaky.
- A fake implementation is fast, predictable, and fully controlled by the test.

### FakeTestRepository (unit tests)

Used in `src/test`. Backed by a `LinkedHashMap` in memory.

```kotlin
class FakeTestRepository : TasksRepository {

    private var shouldReturnError = false
    var tasksServiceData: LinkedHashMap<String, Task> = LinkedHashMap()

    fun setReturnError(value: Boolean) {
        shouldReturnError = value
    }

    override suspend fun getTasks(forceUpdate: Boolean): Result<List<Task>> {
        if (shouldReturnError) return Error(Exception("Error"))
        return Success(tasksServiceData.values.toList())
    }

    override suspend fun completeTask(task: Task) {
        tasksServiceData[task.id]?.isCompleted = true
        refreshTasks()
    }
    // ...
}
```

The `setReturnError` flag lets tests simulate failure scenarios without any network or DB setup:

```kotlin
@Test
fun loadStatisticsWhenTasksAreUnavailable_callErrorToDisplay() = runTest(mainCoroutineRule.dispatcher) {
    tasksRepository.setReturnError(true)
    statisticsViewModel.refresh()
    advanceUntilIdle()

    assertThat(statisticsViewModel.error.getOrAwaitValue(), `is`(true))
}
```

### FakeAndroidTestRepository (instrumented tests)

Same concept, but lives in `src/androidTest`. Used by Fragment tests through the Service Locator.

### FakeDataSource

Used in `DefaultTasksRepositoryTest` to replace both the remote and local `TasksDataSource` implementations, so the repository logic can be tested in isolation.

---

## 6. Testing LiveData

LiveData has two challenges in unit tests:

**Problem 1 — Main thread check**: `MutableLiveData.setValue()` checks if it's running on the main thread. In a JVM test, there is no Android main thread.

**Solution**: `InstantTaskExecutorRule` replaces `ArchTaskExecutor` with a synchronous version that bypasses the thread check.

```kotlin
@get:Rule
var instantExecutorRule = InstantTaskExecutorRule()
```

**Problem 2 — Lazy transformations**: `LiveData.map {}` and `switchMap {}` are lazy — they only activate when there is an active observer. Reading `.value` directly returns `null` or a stale value.

**Solution**: `getOrAwaitValue()` — a test extension that temporarily observes the LiveData, which activates the transformation chain, and blocks until a value arrives.

```kotlin
// In LiveDataTestUtil.kt
fun <T> LiveData<T>.getOrAwaitValue(...): T {
    val latch = CountDownLatch(1)
    val observer = object : Observer<T> {
        override fun onChanged(o: T) {
            data = o
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }
    this.observeForever(observer)
    latch.await(time, timeUnit)
    return data as T
}
```

Usage:

```kotlin
// ✅ Correct — activates the LiveData chain
val value = viewModel.activeTasksPercent.getOrAwaitValue()

// ❌ Wrong — chain is inactive, always returns 0f or null
val value = viewModel.activeTasksPercent.value
```

---

## 7. Testing coroutines

Coroutines in ViewModels use `viewModelScope`, which dispatches on `Dispatchers.Main`. In unit tests, `Dispatchers.Main` doesn't exist.

### MainCoroutineRule

A JUnit `TestWatcher` that installs a `StandardTestDispatcher` as the main dispatcher before each test and resets it after.

```kotlin
@ExperimentalCoroutinesApi
class MainCoroutineRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description?) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}
```

### StandardTestDispatcher — coroutines don't run automatically

`StandardTestDispatcher` queues coroutines instead of running them eagerly. This lets you control exactly when coroutines execute, which is essential for testing loading states.

```kotlin
@Test
fun loadTasks_loading() = runTest(mainCoroutineRule.dispatcher) {
    statisticsViewModel.refresh()
    // At this point, viewModelScope.launch is queued but hasn't run yet
    // _dataLoading = true was set synchronously before the launch
    assertThat(statisticsViewModel.dataLoading.getOrAwaitValue(), `is`(true))

    advanceUntilIdle()  // now run all pending coroutines

    assertThat(statisticsViewModel.dataLoading.getOrAwaitValue(), `is`(false))
}
```

### runTest vs runBlocking

| | `runBlocking` | `runTest` |
|---|---|---|
| Runs on | Current thread | `TestCoroutineDispatcher` |
| Virtual time | No | Yes — `delay()` is instant |
| Recommended | Legacy code | Current standard |

Always prefer `runTest` for new tests. Pass `mainCoroutineRule.dispatcher` to share the same dispatcher that `viewModelScope` uses:

```kotlin
fun myTest() = runTest(mainCoroutineRule.dispatcher) { ... }
```

---

## 8. Testing Fragments with Espresso

Fragment tests run on a real device and use Espresso to interact with the UI.

### launchFragmentInContainer

Launches a single fragment in isolation, without needing to start the full Activity. This is faster and more focused.

```kotlin
val scenario = launchFragmentInContainer<TasksFragment>(Bundle(), R.style.AppTheme)
```

### Mocking Navigation

Fragments use NavController to navigate. In tests, we mock it with Mockito and inject it via `Navigation.setViewNavController()`, then verify that the right action was called.

```kotlin
@Test
fun clickTask_navigateToDetailFragmentOne() = runTest {
    repository.saveTask(Task("First task", "First description", false, "id1"))

    val scenario = launchFragmentInContainer<TasksFragment>(Bundle(), R.style.AppTheme)
    val navController = mock(NavController::class.java)
    scenario.onFragment { fragment ->
        Navigation.setViewNavController(fragment.view!!, navController)
    }

    onView(withId(R.id.tasks_list))
        .perform(RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
            hasDescendant(withText("First task")), click()
        ))

    verify(navController).navigate(
        TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment("id1")
    )
}
```

### Espresso basics

```kotlin
onView(withId(R.id.add_task_fab))       // find a view
    .perform(click())                    // perform an action

onView(withId(R.id.task_detail_title_text))
    .check(matches(withText("My Task"))) // assert a condition
    .check(matches(isDisplayed()))
```

### @SmallTest / @MediumTest / @LargeTest

Annotations that categorize tests by scope and speed, allowing you to run only a subset in CI:

```kotlin
@SmallTest   // unit / DAO tests
@MediumTest  // Fragment tests
@LargeTest   // full end-to-end flows
```

---

## 9. Testing Room — DAO and LocalDataSource

Room tests use an **in-memory database** that is created fresh for each test and destroyed afterwards. They must run on a device because Room uses Android's SQLite implementation.

```kotlin
@RunWith(AndroidJUnit4::class)
@SmallTest
class TasksDaoTest {

    @get:Rule var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: ToDoDatabase

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ToDoDatabase::class.java
        ).build()
    }

    @After
    fun closeDb() = database.close()

    @Test
    fun insertTaskAndGetById() = runTest {
        val task = Task("title", "description")
        database.taskDao().insertTask(task)

        val loaded = database.taskDao().getTaskById(task.id)

        assertThat(loaded.id, `is`(task.id))
        assertThat(loaded.title, `is`(task.title))
    }
}
```

Key points:
- `Room.inMemoryDatabaseBuilder` — no file on disk, auto-cleaned after the test
- `InstantTaskExecutorRule` — needed if the DAO returns `LiveData`
- `@After fun closeDb()` — always close the database to release resources

---

## 10. Dependency injection in tests — Service Locator

Fragment tests can't inject dependencies through the constructor (the Fragment is created by the framework). The solution used here is a **Service Locator** — a global registry that holds the current repository instance.

```kotlin
object ServiceLocator {
    var tasksRepository: TasksRepository? = null

    fun provideTasksRepository(context: Context): TasksRepository {
        return tasksRepository ?: createTasksRepository(context)
    }

    suspend fun resetRepository() {
        tasksRepository = null
    }
}
```

In tests, we inject a fake before launching the fragment and clean up after:

```kotlin
@Before
fun initRepository() {
    repository = FakeAndroidTestRepository()
    ServiceLocator.tasksRepository = repository  // inject fake
}

@After
fun cleanup() = runTest {
    ServiceLocator.resetRepository()  // restore original state
}
```

This pattern is simpler than full DI (like Hilt) but serves the same purpose for tests: make dependencies swappable.

---

## 11. Key utilities

### `LiveDataTestUtil.kt`

Located in `src/test/.../util/`. Provides `getOrAwaitValue()` as a `LiveData` extension. Temporarily observes a `LiveData` (activating lazy transformation chains) and blocks until a value is emitted or a timeout occurs.

### `MainCoroutineRule.kt`

Located in `src/test/`. A `TestWatcher` JUnit rule that swaps `Dispatchers.Main` for a `StandardTestDispatcher` for the duration of each test. All tests that involve coroutines should include it.

### `FakeTestRepository` vs `FakeAndroidTestRepository`

| | `FakeTestRepository` | `FakeAndroidTestRepository` |
|---|---|---|
| Location | `src/test` | `src/androidTest` |
| Used by | ViewModel and Repository unit tests | Fragment instrumented tests |
| Backed by | `LinkedHashMap` in memory | `LinkedHashMap` in memory |

They are functionally similar but live in different source sets because `src/test` classes are not available to the Android test runner.

---

## 12. Running the tests

```bash
# Run all JVM unit tests
./gradlew testDebugUnitTest

# Run all instrumented tests (requires connected device or emulator)
./gradlew connectedDebugAndroidTest

# Run both
./gradlew test connectedAndroidTest
```

Test reports are generated at:
- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Instrumented tests: `app/build/reports/androidTests/connected/index.html`

---

## 13. Pre-requisites

- Android Studio Jellyfish or above
- JDK 17 (`JAVA_HOME` pointing to JDK 17)
- Android SDK with API 21+
- Familiarity with Kotlin, coroutines, LiveData, ViewModel, and Navigation Component

---

## License

Copyright 2019 Google, Inc. Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
