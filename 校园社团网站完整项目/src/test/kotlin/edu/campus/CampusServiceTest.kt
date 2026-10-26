package edu.campus

import edu.campus.model.*
import edu.campus.repository.LocalRepository
import edu.campus.service.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class CampusServiceTest {
    @TempDir lateinit var directory: Path
    private val instant = LocalDateTime.of(2030, 1, 1, 12, 0)
    private fun service() = CampusService(LocalRepository(directory.resolve("state.json"))) { instant }
    private fun request(capacity: Int = 2) = ActivityRequest("测试活动", "活动介绍", "教学楼", "不限", capacity,
        instant.plusDays(1).toString(), instant.plusDays(2).toString())
    private fun assertRule(status: Int, block: () -> Unit) =
        assertEquals(status, assertFailsWith<RuleViolation>(block = block).status)

    @Test fun `club submission remains hidden until teacher approval`() {
        val service = service()
        val event = service.create("u-club", request())
        assertEquals(ActivityStatus.PENDING, event.status)
        assertFalse(service.published("").any { it.activity.id == event.id })
        assertRule(403) { service.review("u-student", event.id, ReviewRequest(true)) }
        assertRule(400) { service.review("u-teacher", event.id, ReviewRequest(false)) }
        service.review("u-teacher", event.id, ReviewRequest(true, "通过"))
        assertTrue(service.published("测试").any { it.activity.id == event.id })
        assertRule(409) { service.review("u-teacher", event.id, ReviewRequest(true)) }
    }

    @Test fun `registration prevents duplicates and survives reload`() {
        val service = service()
        val event = service.create("u-club", request())
        assertRule(409) { service.register("u-student", event.id) }
        service.review("u-teacher", event.id, ReviewRequest(true))
        service.register("u-student", event.id)
        assertRule(409) { service.register("u-student", event.id) }
        assertEquals(1, service().registrations("u-student").size)
        assertEquals("u-student", service.participants("u-club", event.id).single().student.id)
        assertRule(403) { service.participants("u-student", event.id) }
    }

    @Test fun `only one concurrent registration obtains the final place`() {
        val service = service()
        val event = service.create("u-club", request(1))
        service.review("u-teacher", event.id, ReviewRequest(true))
        val second = service.createUser("u-admin", CreateUserRequest("student2", "第二位学生", "password123", Role.STUDENT))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf("u-student", second.id).map { id -> Callable {
                try { service.register(id, event.id); true } catch (_: RuleViolation) { false }
            } }).map { it.get() }
            assertEquals(1, results.count { it })
            assertEquals(1, service.participants("u-club", event.id).size)
        } finally { pool.shutdownNow() }
    }

    @Test fun `cancelled and expired events cannot accept registrations`() {
        val repo = LocalRepository(directory.resolve("state.json"))
        val service = CampusService(repo) { instant }
        val event = service.create("u-club", request())
        service.review("u-teacher", event.id, ReviewRequest(true))
        val later = CampusService(repo) { instant.plusDays(1) }
        assertRule(409) { later.register("u-student", event.id) }
        service.cancel("u-club", event.id)
        assertRule(409) { service.register("u-student", event.id) }
        assertFalse(service.published("").any { it.activity.id == event.id })
    }

    @Test fun `admin creates accounts while disabled users and other clubs are blocked`() {
        val service = service()
        assertRule(403) { service.users("u-student") }
        val staff = service.createUser("u-admin", CreateUserRequest("otherclub", "其他社团", "password123", Role.CLUB_STAFF, "acm"))
        assertRule(403) { service.participants(staff.id, "a-guitar") }
        assertRule(403) { service.cancel(staff.id, "a-guitar") }
        service.setEnabled("u-admin", staff.id, false)
        assertRule(401) { service.current(staff.id) }
        assertRule(401) { service.login(LoginRequest("otherclub", "password123")) }
        assertRule(400) { service.setEnabled("u-admin", "u-admin", false) }
        assertRule(409) { service.createUser("u-admin", CreateUserRequest("student", "重复账号", "password123", Role.STUDENT)) }
    }

    @Test fun `invalid activity input is rejected without persisting changes`() {
        val service = service()
        val before = service.myActivities("u-club").size
        assertRule(400) { service.create("u-club", request(0)) }
        assertRule(400) { service.create("u-club", request().copy(deadline = "bad-date")) }
        assertRule(400) { service.create("u-club", request().copy(startsAt = instant.minusDays(1).toString())) }
        assertEquals(before, service().myActivities("u-club").size)
    }
}
