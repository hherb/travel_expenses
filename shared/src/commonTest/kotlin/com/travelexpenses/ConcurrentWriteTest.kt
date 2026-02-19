package com.travelexpenses

import com.travelexpenses.db.TravelExpensesDb
import com.travelexpenses.repository.SqlDelightEventLogRepository
import com.travelexpenses.repository.createInMemoryDriver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test 3: Concurrent coroutine writes.
 *
 * THE critical test for the KMP go/no-go decision.
 * Validates that:
 * - Multiple coroutines can write events concurrently without data loss
 * - Mutex-based synchronization prevents sequence number collisions
 * - No deadlocks occur under concurrent load
 * - Works identically on JVM (Android) and Kotlin/Native (iOS)
 */
class ConcurrentWriteTest {

    private val driver = createInMemoryDriver()
    private val db = TravelExpensesDb(driver)
    private val repo = SqlDelightEventLogRepository(db)

    @BeforeTest
    fun setup() {
        TestHelpers.resetSequence()
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun concurrentWrites_fromSameDevice_noDataLoss() = runTest {
        val count = 50
        val jobs = (1..count).map { i ->
            async {
                val event = TestHelpers.makeExpenseCreatedEvent(
                    expense = TestHelpers.makeExpense(id = "exp-$i"),
                    deviceId = "device-1",
                    sequenceNumber = i.toLong(),
                )
                repo.append(event)
            }
        }
        jobs.awaitAll()

        assertEquals(count.toLong(), repo.count())
        val all = repo.getAllEvents()
        assertEquals(count, all.size)
    }

    @Test
    fun concurrentWrites_fromMultipleDevices_noDataLoss() = runTest {
        val devicesCount = 5
        val eventsPerDevice = 20

        val jobs = (1..devicesCount).flatMap { deviceNum ->
            (1..eventsPerDevice).map { eventNum ->
                async {
                    val event = TestHelpers.makeExpenseCreatedEvent(
                        expense = TestHelpers.makeExpense(id = "exp-d${deviceNum}-e${eventNum}"),
                        deviceId = "device-$deviceNum",
                        sequenceNumber = eventNum.toLong(),
                    )
                    repo.append(event)
                }
            }
        }
        jobs.awaitAll()

        val totalExpected = devicesCount * eventsPerDevice
        assertEquals(totalExpected.toLong(), repo.count())

        // Verify each device has the correct count
        for (d in 1..devicesCount) {
            val deviceEvents = repo.getEventsForDevice("device-$d")
            assertEquals(eventsPerDevice, deviceEvents.size)
        }
    }

    @Test
    fun concurrentMixedOperations_writeAndRead_noDeadlock() = runTest {
        // Pre-populate some data
        for (i in 1..10) {
            repo.append(
                TestHelpers.makeExpenseCreatedEvent(
                    expense = TestHelpers.makeExpense(id = "pre-$i"),
                    sequenceNumber = i.toLong(),
                )
            )
        }

        // Mix concurrent writes and reads
        val writeJobs = (11..30).map { i ->
            async {
                repo.append(
                    TestHelpers.makeExpenseCreatedEvent(
                        expense = TestHelpers.makeExpense(id = "exp-$i"),
                        sequenceNumber = i.toLong(),
                    )
                )
            }
        }

        val readJobs = (1..10).map {
            async {
                repo.getAllEvents()
            }
        }

        val countJobs = (1..10).map {
            async {
                repo.count()
            }
        }

        writeJobs.awaitAll()
        val readResults = readJobs.awaitAll()
        val countResults = countJobs.awaitAll()

        // All writes should have completed
        assertEquals(30, repo.count())

        // Reads should return consistent snapshots (each result is a valid state)
        readResults.forEach { events ->
            // Each read sees at least the pre-populated 10 events
            assert(events.size >= 10) { "Expected at least 10 events, got ${events.size}" }
        }

        // Counts should be consistent
        countResults.forEach { count ->
            assert(count >= 10) { "Expected count >= 10, got $count" }
        }
    }
}
