package edu.campus.service

import edu.campus.model.*
import edu.campus.repository.LocalRepository
import edu.campus.security.Passwords
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class RuleViolation(val status: Int, override val message: String) : RuntimeException(message)

class CampusService(private val repository: LocalRepository, private val now: () -> LocalDateTime = {
    LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
}) {
    private fun fail(code: Int, message: String): Nothing = throw RuleViolation(code, message)
    private fun requireRule(condition: Boolean, code: Int, message: String) {
        if (!condition) fail(code, message)
    }
    private fun actor(s: State, id: String, vararg roles: Role): User {
        val user = s.users.find { it.id == id && it.enabled } ?: fail(401, "请重新登录")
        requireRule(roles.isEmpty() || user.role in roles, 403, "当前账号没有此操作权限")
        return user
    }
    private fun activity(s: State, id: String) = s.activities.find { it.id == id } ?: fail(404, "活动不存在")
    private fun view(s: State, a: Activity) = ActivityView(a,
        s.clubs.find { it.id == a.clubId }?.name ?: a.clubId,
        s.registrations.count { it.activityId == a.id })

    fun login(request: LoginRequest): PublicUser = repository.read { s ->
        val user = s.users.find { it.username == request.username.trim() && it.enabled }
        requireRule(user != null && Passwords.verify(request.password, user.passwordHash), 401, "账号或密码错误，或账号已禁用")
        user!!.public()
    }
    fun current(id: String): PublicUser = repository.read { actor(it, id).public() }
    fun clubs(): List<Club> = repository.read { it.clubs.toList() }
    fun published(query: String): List<ActivityView> = repository.read { s ->
        s.activities.filter { it.status == ActivityStatus.APPROVED }
            .map { view(s, it) }
            .filter { query.isBlank() || "${it.activity.title} ${it.activity.description} ${it.clubName}".contains(query, true) }
    }
    fun myActivities(id: String): List<ActivityView> = repository.read { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        s.activities.filter { it.clubId == user.clubId }.map { view(s, it) }
    }

    fun create(id: String, request: ActivityRequest): Activity = repository.change { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        requireRule(s.clubs.any { it.id == user.clubId }, 400, "请先关联有效社团")
        requireRule(request.title.trim().length in 2..100, 400, "标题需要 2 至 100 个字符")
        requireRule(request.description.trim().length in 2..3000, 400, "简介需要 2 至 3000 个字符")
        requireRule(request.location.trim().length in 2..200, 400, "请填写有效地点")
        requireRule(request.requirements.trim().length in 1..500, 400, "请填写参加条件，无条件可填不限")
        requireRule(request.capacity in 1..5000, 400, "人数上限应为 1 至 5000")
        val deadline = parseDate(request.deadline)
        val start = parseDate(request.startsAt)
        requireRule(deadline.isAfter(now()) && start.isAfter(deadline), 400, "报名截止时间应晚于现在，活动时间应晚于截止时间")
        Activity(UUID.randomUUID().toString(), user.clubId!!, user.id, request.title.trim(),
            request.description.trim(), request.location.trim(), request.requirements.trim(),
            request.capacity, deadline.toString(), start.toString()).also { s.activities.add(it) }
    }

    private fun parseDate(text: String): LocalDateTime = try { LocalDateTime.parse(text) }
        catch (_: Exception) { fail(400, "日期格式应为 YYYY-MM-DDTHH:mm") }

    fun pending(id: String): List<ActivityView> = repository.read { s ->
        actor(s, id, Role.TEACHER)
        s.activities.filter { it.status == ActivityStatus.PENDING }.map { view(s, it) }
    }
    fun review(id: String, activityId: String, request: ReviewRequest): Activity = repository.change { s ->
        val teacher = actor(s, id, Role.TEACHER)
        val event = activity(s, activityId)
        requireRule(event.status == ActivityStatus.PENDING, 409, "只能审核待审核活动")
        requireRule(request.comment.length <= 1000, 400, "审核意见过长")
        requireRule(request.approved || request.comment.isNotBlank(), 400, "驳回时请填写原因")
        val updated = event.copy(status = if (request.approved) ActivityStatus.APPROVED else ActivityStatus.REJECTED,
            reviewComment = request.comment.trim(), reviewerId = teacher.id)
        s.activities[s.activities.indexOf(event)] = updated
        updated
    }

    fun register(id: String, activityId: String): Registration = repository.change { s ->
        actor(s, id, Role.STUDENT)
        val event = activity(s, activityId)
        requireRule(event.status == ActivityStatus.APPROVED, 409, "活动尚未发布或已取消")
        requireRule(now().isBefore(parseDate(event.deadline)), 409, "报名已截止")
        requireRule(s.registrations.none { it.activityId == activityId && it.studentId == id }, 409, "你已经报名此活动")
        requireRule(s.registrations.count { it.activityId == activityId } < event.capacity, 409, "报名人数已满")
        Registration(UUID.randomUUID().toString(), activityId, id, now().toString()).also { s.registrations.add(it) }
    }
    fun registrations(id: String): List<RegistrationView> = repository.read { s ->
        actor(s, id, Role.STUDENT)
        s.registrations.filter { it.studentId == id }.map { RegistrationView(it, view(s, activity(s, it.activityId))) }
    }
    fun participants(id: String, activityId: String): List<Participant> = repository.read { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        requireRule(activity(s, activityId).clubId == user.clubId, 403, "只能查看本社团的报名名单")
        s.registrations.filter { it.activityId == activityId }.map { r ->
            Participant(s.users.first { it.id == r.studentId }.public(), r.createdAt)
        }
    }
    fun cancel(id: String, activityId: String): Activity = repository.change { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        val event = activity(s, activityId)
        requireRule(event.clubId == user.clubId, 403, "只能取消本社团的活动")
        val updated = event.copy(status = ActivityStatus.CANCELLED)
        s.activities[s.activities.indexOf(event)] = updated
        updated
    }

    fun users(id: String): List<PublicUser> = repository.read { s ->
        actor(s, id, Role.ADMIN)
        s.users.map { it.public() }
    }
    fun createUser(id: String, request: CreateUserRequest): PublicUser = repository.change { s ->
        actor(s, id, Role.ADMIN)
        val username = request.username.trim()
        requireRule(username.matches(Regex("[a-zA-Z0-9_]{3,30}")), 400, "账号需要 3 至 30 位字母、数字或下划线")
        requireRule(request.name.trim().length in 1..50, 400, "姓名需要 1 至 50 个字符")
        requireRule(request.password.length in 8..128, 400, "密码需要 8 至 128 个字符")
        requireRule(s.users.none { it.username.equals(username, true) }, 409, "账号已存在")
        requireRule(request.role != Role.CLUB_STAFF || s.clubs.any { it.id == request.clubId }, 400, "社团工作人员必须关联有效社团")
        val user = User(UUID.randomUUID().toString(), username, request.name.trim(), Passwords.hash(request.password),
            request.role, if (request.role == Role.CLUB_STAFF) request.clubId else null)
        s.users.add(user)
        user.public()
    }
    fun setEnabled(id: String, targetId: String, enabled: Boolean): PublicUser = repository.change { s ->
        actor(s, id, Role.ADMIN)
        val user = s.users.find { it.id == targetId } ?: fail(404, "账号不存在")
        requireRule(targetId != id || enabled, 400, "不能禁用当前管理员账号")
        val updated = user.copy(enabled = enabled)
        s.users[s.users.indexOf(user)] = updated
        updated.public()
    }
}
