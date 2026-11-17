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
    @Test fun `edited event keeps registrations requires reapproval and exposes changes`() {
        val service = service()
        val event = service.create("u-club", request())
        service.review("u-teacher", event.id, ReviewRequest(true))
        service.register("u-student", event.id)
        val other = service.createUser("u-admin", CreateUserRequest("otherstaff", "负责人", "password123", Role.CLUB_STAFF, "acm"))
        assertRule(403) { service.edit(other.id, event.id, request()) }
        assertRule(400) { service.edit("u-club", event.id, request(1).copy(endsAt = instant.toString())) }
        val updated = service.edit("u-club", event.id, request().copy(location = "新地点"))
        assertEquals(ActivityStatus.PENDING, updated.status)
        assertNotNull(updated.updatedAt)
        assertFalse(service.published("").any { it.activity.id == event.id })
        assertEquals("新地点", service.registrations("u-student").single().event.activity.location)
        assertEquals(1, service.allParticipants("u-admin", event.id).size)
        assertRule(403) { service.allParticipants("u-student", event.id) }
        service.review("u-teacher", event.id, ReviewRequest(true))
        assertEquals(1, service.participants("u-club", event.id).size)
        assertEquals(ActivityStatus.APPROVED, service().allActivities("u-admin").first { it.activity.id == event.id }.activity.status)
    }

    @Test fun `teacher can set end on approved events with role and time validation`() {
        val service = service()
        val event = service.create("u-club", request())
        assertRule(409) { service.setEndTime("u-teacher", event.id, instant.plusDays(3).toString()) }
        service.review("u-teacher", event.id, ReviewRequest(true))
        assertRule(403) { service.setEndTime("u-student", event.id, instant.plusDays(3).toString()) }
        assertRule(400) { service.setEndTime("u-teacher", event.id, instant.plusDays(1).toString()) }
        val changed = service.setEndTime("u-teacher", event.id, instant.plusDays(3).toString())
        assertNotNull(changed.updatedAt)
        assertTrue(service.reviewed("u-teacher").any { it.activity.id == event.id })
    }
    @Test fun `category history notifications and read state survive reload with permission checks`() {
        val service = service()
        val event = service.create("u-club", request().copy(category = "音乐"))
        service.review("u-teacher", event.id, ReviewRequest(true))
        assertTrue(service.published("", "音乐").any { it.activity.id == event.id })
        assertFalse(service.published("", "运动").any { it.activity.id == event.id })
        service.register("u-student", event.id)
        service.edit("u-club", event.id, request().copy(category = "艺术", location = "新地点"))
        val notice = service.messages("u-student").first()
        assertTrue(notice.text.contains("新地点"))
        assertTrue(service.history("u-student", event.id).single().summary.contains("新地点"))
        assertRule(404) { service.markRead("u-club", notice.id) }
        service.markRead("u-student", notice.id)
        val reloaded = service()
        assertTrue(reloaded.messages("u-student").first().read)
        assertEquals(1, reloaded.history("u-student", event.id).size)
        assertRule(403) { reloaded.history("u-student", "a-guitar") }
    }

    @Test fun `club application requires teacher review and keeps original club managed`() {
        val service = service()
        val app = service.applyClub("u-club", ClubApplicationRequest("摄影协会", "一起交流摄影"))
        assertRule(403) { service.reviewClub("u-student", app.id, ReviewRequest(true)) }
        assertRule(409) { service.reviewClub("u-teacher", app.id, ReviewRequest(true)) }
        service.createUser("u-admin", CreateUserRequest("backup", "原社团负责人", "password123", Role.CLUB_STAFF, "guitar"))
        assertEquals(ActivityStatus.APPROVED, service.reviewClub("u-teacher", app.id, ReviewRequest(true)).status)
        assertEquals("摄影协会", service.clubs().first { it.id == service.current("u-club").clubId }.name)
        assertTrue(service.messages("u-club").first().text.contains("已通过"))
        assertEquals(2, service.staffList("u-teacher").size)
        assertRule(409) { service.reviewClub("u-teacher", app.id, ReviewRequest(true)) }
        assertRule(403) { service.staffList("u-student") }
    }

    @Test fun `legacy state loads without newly added fields`() {
        val path = directory.resolve("legacy.json")
        java.nio.file.Files.writeString(path, """{"users":[],"clubs":[],"activities":[{"id":"old","clubId":"c","creatorId":"u","title":"旧活动","description":"旧介绍","location":"旧地点","requirements":"不限","capacity":3,"deadline":"2099-01-01T00:00","startsAt":"2099-01-02T00:00"}],"registrations":[]}""")
        val state = LocalRepository(path).read { it }
        assertEquals("其他", state.activities.single().category)
        assertTrue(state.notifications.isEmpty())
        assertTrue(state.history.isEmpty())
        assertTrue(state.clubApplications.isEmpty())
    }
    @Test fun `posts require review before publication and keep review history`() {
        val service = service()
        val post = service.savePost("u-club", null, ClubPostRequest("社团招新", "欢迎报名参加我们的社团"))
        assertTrue(service.publicPosts().none { it.post.id == post.id })
        assertRule(403) { service.reviewPost("u-student", post.id, ReviewRequest(true)) }
        assertRule(400) { service.reviewPost("u-teacher", post.id, ReviewRequest(false)) }
        service.reviewPost("u-teacher", post.id, ReviewRequest(false, "请补充地点"))
        assertTrue(service.clubPosts("u-club").single().post.reviewComment.contains("地点"))
        service.savePost("u-club", post.id, ClubPostRequest("社团招新", "活动中心见"))
        service.reviewPost("u-teacher", post.id, ReviewRequest(true, "通过"))
        assertTrue(service.publicPosts().any { it.post.id == post.id })
        val records = service().reviewHistory("u-club").filter { it.targetId == post.id }
        assertEquals(2, records.size)
        assertEquals(listOf(true, false), records.map { it.approved })
        assertEquals("请补充地点", records.last().comment)
        assertRule(403) { service.reviewHistory("u-student") }
        val other = service.createUser("u-admin", CreateUserRequest("poststaff", "另一社团", "password123", Role.CLUB_STAFF, "acm"))
        assertRule(403) { service.savePost(other.id, post.id, ClubPostRequest("替换公告", "无权操作")) }
        assertTrue(service.reviewHistory(other.id).isEmpty())
    }

    @Test fun `reminders are private scheduled once and persist across reload`() {
        val repo = LocalRepository(directory.resolve("reminders.json"))
        var clock = instant
        val service = CampusService(repo) { clock }
        val event = service.create("u-club", request())
        service.review("u-teacher", event.id, ReviewRequest(true))
        val reg = service.register("u-student", event.id)
        assertRule(400) { service.setReminder("u-student", reg.id, 4) }
        val other = service.createUser("u-admin", CreateUserRequest("reminder2", "另一学生", "password123", Role.STUDENT))
        assertRule(404) { service.setReminder(other.id, reg.id, 60) }
        service.setReminder("u-student", reg.id, 60)
        service.processReminders()
        assertFalse(service.messages("u-student").any { it.text.startsWith("活动提醒") })
        clock = instant.plusDays(2).minusMinutes(59)
        CampusService(LocalRepository(directory.resolve("reminders.json"))) { clock }.processReminders()
        val reloaded = CampusService(LocalRepository(directory.resolve("reminders.json"))) { clock }
        assertEquals(1, reloaded.messages("u-student").count { it.text.startsWith("活动提醒") })
        reloaded.processReminders()
        assertEquals(1, reloaded.messages("u-student").count { it.text.startsWith("活动提醒") })
        reloaded.setReminder("u-student", reg.id, null)
        assertNull(reloaded.registrations("u-student").single().reminderMinutes)
    }
}
