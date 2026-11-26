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
        assertTrue(state.accountApplications.isEmpty())
        assertTrue(state.clubMemberships.isEmpty())
        assertFalse(state.activities.single().membersOnly)
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
    @Test fun `self registration supports students and guards privileged roles until approval`() {
        val service = service()
        assertFalse(service.registerAccount(RegisterRequest("新同学", "password123", Role.STUDENT)).requiresApproval)
        assertEquals(Role.STUDENT, service.login(LoginRequest("新同学", "password123")).role)
        assertRule(409) { service.registerAccount(RegisterRequest("新同学", "password123", Role.STUDENT)) }
        assertRule(400) { service.registerAccount(RegisterRequest("社团小王", "password123", Role.CLUB_STAFF)) }
        for ((role, nickname) in listOf(Role.CLUB_STAFF to "社团小王", Role.TEACHER to "新老师", Role.ADMIN to "新管理员")) {
            val result = service.registerAccount(RegisterRequest(nickname, "password123", role, if (role == Role.CLUB_STAFF) "guitar" else null))
            assertTrue(result.requiresApproval)
            assertRule(403) { service.login(LoginRequest(nickname, "password123")) }
            assertRule(403) { service.accountApplications("u-student") }
            val pending = service.accountApplications("u-admin").first { it.nickname == nickname }
            assertRule(403) { service.decideAccountApplication("u-student", pending.id, AccountDecisionRequest(true)) }
            service.decideAccountApplication("u-admin", pending.id, AccountDecisionRequest(true))
            val user = service().login(LoginRequest(nickname, "password123"))
            assertEquals(role, user.role)
            if (role == Role.CLUB_STAFF) assertEquals("guitar", user.clubId)
            assertRule(409) { service.decideAccountApplication("u-admin", pending.id, AccountDecisionRequest(true)) }
        }
    }

    @Test fun `registration rejection allows reapplication without bypassing approval`() {
        val service = service()
        service.registerAccount(RegisterRequest("TeacherOne", "password123", Role.TEACHER))
        assertRule(409) { service.registerAccount(RegisterRequest("teacherone", "password123", Role.STUDENT)) }
        assertRule(409) { service.createUser("u-admin", CreateUserRequest("teacherone", "重复", "password123", Role.TEACHER)) }
        val pending = service.accountApplications("u-admin").single()
        assertRule(400) { service.decideAccountApplication("u-admin", pending.id, AccountDecisionRequest(false)) }
        service.decideAccountApplication("u-admin", pending.id, AccountDecisionRequest(false, "请提供身份信息"))
        assertTrue(assertFailsWith<RuleViolation> { service.login(LoginRequest("teacherone", "password123")) }.message.contains("请提供身份信息"))
        assertEquals(401, assertFailsWith<RuleViolation> { service.login(LoginRequest("teacherone", "wrong")) }.status)
        service.registerAccount(RegisterRequest("TeacherOne", "password123", Role.TEACHER))
        assertRule(403) { service.login(LoginRequest("TeacherOne", "password123")) }
        assertEquals(1, service.accountApplications("u-admin").size)
    }
    @Test fun `membership needs approval and restricted activity checks reviewed membership`() {
        val service = service()
        val event = service.create("u-club", request().copy(membersOnly = true))
        service.review("u-teacher", event.id, ReviewRequest(true))
        assertRule(409) { service.register("u-student", event.id) }
        assertTrue(service.messages("u-student").first().text.contains("仅限所属社团成员"))
        val application = service.joinClub("u-student", "guitar")
        assertEquals(MembershipStatus.PENDING, application.status)
        assertTrue(service.myClubs("u-student").isEmpty())
        assertRule(409) { service.register("u-student", event.id) }
        assertRule(403) { service.reviewMembership("u-teacher", application.id, ReviewRequest(true)) }
        val other = service.createUser("u-admin", CreateUserRequest("otherstaff2", "其他社团", "password123", Role.CLUB_STAFF, "acm"))
        assertRule(403) { service.reviewMembership(other.id, application.id, ReviewRequest(true)) }
        assertRule(400) { service.reviewMembership("u-club", application.id, ReviewRequest(false)) }
        service.reviewMembership("u-club", application.id, ReviewRequest(true))
        assertTrue(service.messages("u-student").first().text.contains("欢迎加入"))
        assertEquals("guitar", service.myClubs("u-student").single().id)
        assertEquals(1, service().myClubs("u-student").size)
        service.register("u-student", event.id)
        assertTrue(service.messages("u-student").first().text.contains("恭喜成功报名"))
        assertRule(409) { service.joinClub("u-student", "guitar") }
    }

    @Test fun `student registration can request a club and rejection permits reapplication`() {
        val service = service()
        assertRule(400) { service.registerAccount(RegisterRequest("成员学生", "password123", Role.STUDENT, "missing")) }
        service.registerAccount(RegisterRequest("成员学生", "password123", Role.STUDENT, "guitar"))
        val student = service.login(LoginRequest("成员学生", "password123"))
        val pending = service.pendingMemberships("u-club").single()
        assertEquals(student.id, pending.studentId)
        assertTrue(service.messages("u-club").any { it.text.contains("申请加入") })
        service.reviewMembership("u-club", pending.id, ReviewRequest(false, "资料待补充"))
        assertTrue(service.messages(student.id).first().text.contains("资料待补充"))
        assertTrue(service.myClubs(student.id).isEmpty())
        service.joinClub(student.id, "guitar")
        assertEquals(MembershipStatus.PENDING, service.myMemberships(student.id).single().status)
    }

    @Test fun `failure notices survive failed attempts and event change notifies registrations`() {
        val repo = LocalRepository(directory.resolve("notice-state.json"))
        val service = CampusService(repo) { instant }
        val event = service.create("u-club", request(1))
        service.review("u-teacher", event.id, ReviewRequest(true))
        service.register("u-student", event.id)
        assertRule(404) { service.register("u-student", "missing-activity") }
        assertTrue(service.messages("u-student").first().text.contains("活动不存在"))
        val other = service.createUser("u-admin", CreateUserRequest("otherstudent2", "其他学生", "password123", Role.STUDENT))
        assertRule(409) { service.register(other.id, event.id) }
        assertTrue(CampusService(LocalRepository(directory.resolve("notice-state.json"))) { instant }.messages(other.id).single().text.contains("人数已满"))
        val later = CampusService(repo) { instant.plusDays(1) }
        assertRule(409) { later.register(other.id, event.id) }
        assertTrue(service.messages(other.id).first().text.contains("截止"))
        assertRule(409) { service.edit("u-club", event.id, request(1).copy(membersOnly = true)) }
        service.edit("u-club", event.id, request(1).copy(startsAt = instant.plusDays(3).toString()))
        assertTrue(service.messages("u-student").first().text.contains("开始时间"))
        service.cancel("u-club", event.id)
        assertTrue(service.messages("u-student").first().text.contains("已取消"))
    }
}
