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
        s.registrations.count { it.activityId == a.id },
        s.users.find { it.id == a.creatorId }?.name ?: "未知")

    private fun notify(s: State, userId: String, text: String) {
        s.notifications.add(Notification(UUID.randomUUID().toString(), userId, text, now().toString()))
    }
    private fun logChange(s: State, activityId: String, actorId: String, summary: String) {
        s.history.add(ActivityChange(UUID.randomUUID().toString(), activityId, actorId, summary, now().toString()))
    }
    private fun recordReview(s: State, kind: String, targetId: String, title: String, clubId: String, reviewer: User, request: ReviewRequest) {
        s.reviews.add(ReviewRecord(UUID.randomUUID().toString(), kind, targetId, title, clubId, reviewer.id, reviewer.name,
            request.approved, request.comment.trim(), now().toString()))
    }
    fun reviewHistory(id: String): List<ReviewRecord> = repository.read { s ->
        val user = actor(s, id, Role.TEACHER, Role.ADMIN, Role.CLUB_STAFF)
        s.reviews.filter { user.role != Role.CLUB_STAFF || it.clubId == user.clubId }.asReversed()
    }
    private fun postView(s: State, p: ClubPost) = PostView(p,
        s.clubs.find { it.id == p.clubId }?.name ?: "未知社团", s.users.find { it.id == p.creatorId }?.name ?: "未知")
    fun publicPosts(): List<PostView> = repository.read { s -> s.posts.filter { it.status == ActivityStatus.APPROVED }.asReversed().map { postView(s, it) } }
    fun clubPosts(id: String): List<PostView> = repository.read { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        s.posts.filter { it.clubId == user.clubId }.asReversed().map { postView(s, it) }
    }
    fun pendingPosts(id: String): List<PostView> = repository.read { s ->
        actor(s, id, Role.TEACHER)
        s.posts.filter { it.status == ActivityStatus.PENDING }.map { postView(s, it) }
    }
    fun savePost(id: String, postId: String?, request: ClubPostRequest): ClubPost = repository.change { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        requireRule(s.clubs.any { it.id == user.clubId }, 400, "请先关联有效社团")
        requireRule(request.title.trim().length in 2..100 && request.content.trim().length in 2..5000, 400, "标题应为 2 至 100 字，正文应为 2 至 5000 字")
        val old = postId?.let { pid -> s.posts.find { it.id == pid } ?: fail(404, "公告不存在") }
        if (old != null) requireRule(old.clubId == user.clubId, 403, "只能编辑本社团公告")
        val post = old?.copy(title = request.title.trim(), content = request.content.trim(), status = ActivityStatus.PENDING, reviewComment = "")
            ?: ClubPost(UUID.randomUUID().toString(), user.clubId!!, id, request.title.trim(), request.content.trim(), now().toString())
        if (old == null) s.posts.add(post) else s.posts[s.posts.indexOf(old)] = post
        post
    }
    fun reviewPost(id: String, postId: String, request: ReviewRequest): ClubPost = repository.change { s ->
        val teacher = actor(s, id, Role.TEACHER)
        val post = s.posts.find { it.id == postId } ?: fail(404, "公告不存在")
        requireRule(post.status == ActivityStatus.PENDING, 409, "只能审核待审核公告")
        requireRule(request.comment.length <= 1000 && (request.approved || request.comment.isNotBlank()), 400, "驳回时必须填写意见")
        val updated = post.copy(status = if (request.approved) ActivityStatus.APPROVED else ActivityStatus.REJECTED, reviewComment = request.comment.trim())
        s.posts[s.posts.indexOf(post)] = updated
        recordReview(s, "公告", post.id, post.title, post.clubId, teacher, request)
        notify(s, post.creatorId, "公告「${post.title}」${if (request.approved) "已通过审核" else "未通过审核：${request.comment.trim()}"}")
        updated
    }
    fun setReminder(id: String, registrationId: String, minutes: Int?): Reminder? = repository.change { s ->
        actor(s, id, Role.STUDENT)
        val registration = s.registrations.find { it.id == registrationId && it.studentId == id } ?: fail(404, "报名记录不存在")
        val event = activity(s, registration.activityId)
        requireRule(minutes == null || minutes in listOf(60, 1440, 10080), 400, "请选择有效提醒时间")
        requireRule(minutes == null || event.status == ActivityStatus.APPROVED && now().isBefore(parseDate(event.startsAt)), 409, "仅可为未开始的已发布活动设置提醒")
        val old = s.reminders.find { it.registrationId == registrationId }
        if (minutes == null) { if (old != null) s.reminders.remove(old); null }
        else {
            val reminder = Reminder(registrationId, minutes, old?.sentFor)
            if (old == null) s.reminders.add(reminder) else s.reminders[s.reminders.indexOf(old)] = reminder
            reminder
        }
    }
    fun processReminders() {
        val due = repository.read { s -> s.reminders.any { r ->
            val reg = s.registrations.find { it.id == r.registrationId }
            val event = s.activities.find { it.id == reg?.activityId }
            val user = s.users.find { it.id == reg?.studentId }
            event != null && user?.enabled == true && event.status == ActivityStatus.APPROVED && r.sentFor != event.startsAt &&
                !now().isBefore(parseDate(event.startsAt).minusMinutes(r.minutesBefore.toLong())) && now().isBefore(parseDate(event.startsAt))
        } }
        if (!due) return
        repository.change { s ->
            s.reminders.toList().forEach { r ->
                val reg = s.registrations.find { it.id == r.registrationId }
                val event = s.activities.find { it.id == reg?.activityId }
                val user = s.users.find { it.id == reg?.studentId }
                if (reg != null && event != null && user?.enabled == true && event.status == ActivityStatus.APPROVED && r.sentFor != event.startsAt &&
                    !now().isBefore(parseDate(event.startsAt).minusMinutes(r.minutesBefore.toLong())) && now().isBefore(parseDate(event.startsAt))) {
                    notify(s, reg.studentId, "活动提醒：「${event.title}」将于 ${event.startsAt.replace('T', ' ')} 开始，地点：${event.location}")
                    s.reminders[s.reminders.indexOf(r)] = r.copy(sentFor = event.startsAt)
                }
            }
        }
    }

    fun messages(id: String): List<Notification> = repository.read { s ->
        actor(s, id)
        s.notifications.filter { it.userId == id }.asReversed()
    }
    fun markRead(id: String, messageId: String): Notification = repository.change { s ->
        actor(s, id)
        val message = s.notifications.find { it.id == messageId && it.userId == id } ?: fail(404, "消息不存在")
        val updated = message.copy(read = true)
        s.notifications[s.notifications.indexOf(message)] = updated
        updated
    }
    fun history(id: String, activityId: String): List<ActivityChange> = repository.read { s ->
        val user = actor(s, id)
        val event = activity(s, activityId)
        requireRule(user.role == Role.ADMIN || user.role == Role.TEACHER ||
            (user.role == Role.CLUB_STAFF && user.clubId == event.clubId) ||
            (user.role == Role.STUDENT && s.registrations.any { it.studentId == id && it.activityId == activityId }), 403, "无权查看活动修改记录")
        s.history.filter { it.activityId == activityId }.asReversed()
    }
    fun applyClub(id: String, request: ClubApplicationRequest): ClubApplication = repository.change { s ->
        actor(s, id, Role.CLUB_STAFF)
        val name = request.name.trim(); val description = request.description.trim()
        requireRule(name.length in 2..80 && description.length in 2..1000, 400, "社团名称或简介长度不正确")
        requireRule(s.clubs.none { it.name.equals(name, true) } && s.clubApplications.none { it.name.equals(name, true) && it.status == ActivityStatus.PENDING }, 409, "该社团已存在或正在审核")
        requireRule(s.clubApplications.none { it.applicantId == id && it.status == ActivityStatus.PENDING }, 409, "已有待审核的社团申请")
        ClubApplication(UUID.randomUUID().toString(), name, description, id, createdAt = now().toString()).also { s.clubApplications.add(it) }
    }
    fun clubApplications(id: String): List<ClubApplication> = repository.read { s ->
        val user = actor(s, id, Role.CLUB_STAFF, Role.TEACHER, Role.ADMIN)
        if (user.role == Role.CLUB_STAFF) s.clubApplications.filter { it.applicantId == id }.asReversed() else s.clubApplications.asReversed()
    }
    fun reviewClub(id: String, applicationId: String, request: ReviewRequest): ClubApplication = repository.change { s ->
        val teacher = actor(s, id, Role.TEACHER)
        val application = s.clubApplications.find { it.id == applicationId } ?: fail(404, "社团申请不存在")
        requireRule(application.status == ActivityStatus.PENDING, 409, "申请已审核")
        requireRule(request.comment.length <= 1000 && (request.approved || request.comment.isNotBlank()), 400, "驳回时必须填写意见")
        if (request.approved) {
            requireRule(s.clubs.none { it.name.equals(application.name, true) }, 409, "该社团已存在")
            val applicant = s.users.first { it.id == application.applicantId }
            requireRule(s.activities.none { it.clubId == applicant.clubId } || s.users.any { it.id != applicant.id && it.clubId == applicant.clubId && it.role == Role.CLUB_STAFF && it.enabled }, 409, "申请人原社团有活动且没有其他负责人，不能转移，请先安排负责人")
            val newClub = Club(UUID.randomUUID().toString(), application.name)
            s.clubs.add(newClub)
            s.users[s.users.indexOf(applicant)] = applicant.copy(clubId = newClub.id)
        }
        val updated = application.copy(status = if (request.approved) ActivityStatus.APPROVED else ActivityStatus.REJECTED, comment = request.comment.trim())
        s.clubApplications[s.clubApplications.indexOf(application)] = updated
        recordReview(s, "社团", application.id, application.name, s.users.first { it.id == application.applicantId }.clubId.orEmpty(), teacher, request)
        notify(s, application.applicantId, "社团申请「${application.name}」${if (request.approved) "已通过" else "未通过：${request.comment.trim()}"}")
        updated
    }
    fun staffList(id: String): List<PublicUser> = repository.read { s ->
        actor(s, id, Role.TEACHER, Role.ADMIN)
        s.users.filter { it.role == Role.CLUB_STAFF }.map { it.public() }
    }

    fun registerAccount(request: RegisterRequest): RegisterResult = repository.change { s ->
        val nickname = request.nickname.trim()
        requireRule(nickname.length in 2..30 && nickname.none { it.isISOControl() }, 400, "昵称需要 2 至 30 个字符，不能包含控制字符")
        requireRule(request.password.length in 8..128, 400, "密码需要 8 至 128 个字符")
        requireRule(s.users.none { it.username.equals(nickname, true) } &&
            s.accountApplications.none { it.status == AccountApplicationStatus.PENDING && it.nickname.equals(nickname, true) }, 409, "该昵称已被使用或正在审核")
        requireRule(request.role != Role.CLUB_STAFF || s.clubs.any { it.id == request.clubId }, 400, "社团负责人请选择有效的所属社团")
        requireRule(request.role != Role.STUDENT || request.clubId == null || s.clubs.any { it.id == request.clubId }, 400, "请选择有效社团或选择暂无社团")
        val hash = Passwords.hash(request.password)
        if (request.role == Role.STUDENT) {
            val student = User(UUID.randomUUID().toString(), nickname, nickname, hash, Role.STUDENT)
            s.users.add(student)
            if (request.clubId != null) {
                val club = s.clubs.first { it.id == request.clubId }
                val membership = ClubMembership(UUID.randomUUID().toString(), club.id, student.id, now().toString())
                s.clubMemberships.add(membership)
                notify(s, student.id, "已提交加入「${club.name}」的申请，请等待社团负责人审核。")
                s.users.filter { it.role == Role.CLUB_STAFF && it.enabled && it.clubId == club.id }.forEach {
                    notify(s, it.id, "${student.name}申请加入「${club.name}」，请在成员审核中处理。")
                }
            }
            RegisterResult("学生账号注册成功，请使用昵称和密码登录", false)
        } else {
            s.accountApplications.add(AccountApplication(UUID.randomUUID().toString(), nickname, hash, request.role,
                if (request.role == Role.CLUB_STAFF) request.clubId else null, now().toString()))
            RegisterResult("注册申请已提交，请等待管理员审核后登录", true)
        }
    }
    fun accountApplications(id: String): List<AccountApplicationView> = repository.read { s ->
        actor(s, id, Role.ADMIN)
        s.accountApplications.filter { it.status == AccountApplicationStatus.PENDING }.map {
            AccountApplicationView(it.id, it.nickname, it.role, it.clubId, s.clubs.find { c -> c.id == it.clubId }?.name, it.createdAt)
        }
    }
    fun decideAccountApplication(id: String, applicationId: String, request: AccountDecisionRequest): Message = repository.change { s ->
        val admin = actor(s, id, Role.ADMIN)
        val application = s.accountApplications.find { it.id == applicationId } ?: fail(404, "注册申请不存在")
        requireRule(application.status == AccountApplicationStatus.PENDING, 409, "该申请已处理")
        requireRule(request.comment.length <= 500, 400, "审核意见不能超过 500 个字符")
        requireRule(request.approved || request.comment.isNotBlank(), 400, "驳回时请填写原因")
        if (request.approved) {
            requireRule(s.users.none { it.username.equals(application.nickname, true) }, 409, "昵称已被使用")
            requireRule(application.role != Role.CLUB_STAFF || s.clubs.any { it.id == application.clubId }, 409, "申请的社团不存在")
            s.users.add(User(UUID.randomUUID().toString(), application.nickname, application.nickname,
                application.passwordHash, application.role, application.clubId))
        }
        val updated = application.copy(status = if (request.approved) AccountApplicationStatus.APPROVED else AccountApplicationStatus.REJECTED,
            reviewerId = admin.id, reviewedAt = now().toString(), reviewComment = request.comment.trim())
        s.accountApplications[s.accountApplications.indexOf(application)] = updated
        Message(if (request.approved) "账号已审核通过" else "注册申请已驳回")
    }
    fun login(request: LoginRequest): PublicUser = repository.read { s ->
        val name = request.username.trim()
        val user = s.users.find { it.username.equals(name, true) }
        if (user != null) {
            requireRule(user.enabled && Passwords.verify(request.password, user.passwordHash), 401, "账号或密码错误，或账号已禁用")
            user.public()
        } else {
            val application = s.accountApplications.lastOrNull { it.nickname.equals(name, true) && it.status != AccountApplicationStatus.APPROVED }
            if (application != null && Passwords.verify(request.password, application.passwordHash)) {
                if (application.status == AccountApplicationStatus.PENDING) fail(403, "账号正在等待管理员审核，请稍后再登录")
                fail(403, "注册申请未通过：${application.reviewComment}。请重新注册或联系管理员")
            }
            fail(401, "账号或密码错误，或账号已禁用")
        }
    }
    fun current(id: String): PublicUser = repository.read { actor(it, id).public() }
    fun clubs(): List<Club> = repository.read { it.clubs.toList() }
    private fun membershipView(s: State, m: ClubMembership) = MembershipView(m.id, m.clubId,
        s.clubs.find { it.id == m.clubId }?.name ?: "未知社团", m.studentId,
        s.users.find { it.id == m.studentId }?.name ?: "未知学生", m.status, m.requestedAt, m.reviewComment)
    fun myClubs(id: String): List<Club> = repository.read { s ->
        actor(s, id, Role.STUDENT)
        val clubIds = s.clubMemberships.filter { it.studentId == id && it.status == MembershipStatus.APPROVED }.map { it.clubId }.toSet()
        s.clubs.filter { it.id in clubIds }
    }
    fun myMemberships(id: String): List<MembershipView> = repository.read { s ->
        actor(s, id, Role.STUDENT)
        s.clubMemberships.filter { it.studentId == id }.asReversed().distinctBy { it.clubId }.map { membershipView(s, it) }
    }
    fun joinClub(id: String, clubId: String): ClubMembership = repository.change { s ->
        val student = actor(s, id, Role.STUDENT)
        val club = s.clubs.find { it.id == clubId } ?: fail(404, "社团不存在")
        requireRule(s.clubMemberships.none { it.studentId == id && it.clubId == clubId && it.status != MembershipStatus.REJECTED }, 409, "你已加入该社团或申请正在审核")
        val membership = ClubMembership(UUID.randomUUID().toString(), club.id, id, now().toString())
        s.clubMemberships.add(membership)
        notify(s, id, "已提交加入「${club.name}」的申请，请等待社团负责人审核。")
        s.users.filter { it.role == Role.CLUB_STAFF && it.enabled && it.clubId == club.id }.forEach {
            notify(s, it.id, "${student.name}申请加入「${club.name}」，请在成员审核中处理。")
        }
        membership
    }
    fun pendingMemberships(id: String): List<MembershipView> = repository.read { s ->
        val staff = actor(s, id, Role.CLUB_STAFF)
        s.clubMemberships.filter { it.clubId == staff.clubId && it.status == MembershipStatus.PENDING }.map { membershipView(s, it) }
    }
    fun reviewMembership(id: String, membershipId: String, request: ReviewRequest): MembershipView = repository.change { s ->
        val staff = actor(s, id, Role.CLUB_STAFF)
        val membership = s.clubMemberships.find { it.id == membershipId } ?: fail(404, "成员申请不存在")
        requireRule(staff.clubId == membership.clubId, 403, "只能审核本社团的申请")
        requireRule(membership.status == MembershipStatus.PENDING, 409, "该申请已处理")
        requireRule(request.comment.length <= 500, 400, "审核意见不能超过 500 个字符")
        requireRule(request.approved || request.comment.isNotBlank(), 400, "驳回时请填写原因")
        val updated = membership.copy(status = if (request.approved) MembershipStatus.APPROVED else MembershipStatus.REJECTED,
            reviewedAt = now().toString(), reviewerId = staff.id, reviewComment = request.comment.trim())
        s.clubMemberships[s.clubMemberships.indexOf(membership)] = updated
        val club = s.clubs.first { it.id == membership.clubId }
        notify(s, membership.studentId, if (request.approved) "欢迎加入「${club.name}」！你的申请已通过，可以在我的社团中查看。" else "加入「${club.name}」的申请未通过：${request.comment.trim()}。")
        membershipView(s, updated)
    }
    fun published(query: String, category: String = ""): List<ActivityView> = repository.read { s ->
        s.activities.filter { it.status == ActivityStatus.APPROVED }
            .map { view(s, it) }
            .filter { (category.isBlank() || it.activity.category == category) && (query.isBlank() || "${it.activity.title} ${it.activity.description} ${it.clubName}".contains(query, true)) }
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
        requireRule(request.category in listOf("音乐", "运动", "学术", "艺术", "公益", "其他"), 400, "活动分类无效")
        val deadline = parseDate(request.deadline)
        val start = parseDate(request.startsAt)
        requireRule(deadline.isAfter(now()) && start.isAfter(deadline), 400, "报名截止时间应晚于现在，活动时间应晚于截止时间")
        requireRule(request.endsAt.isBlank() || parseDate(request.endsAt).isAfter(start), 400, "结束时间应晚于开始时间")
        Activity(UUID.randomUUID().toString(), user.clubId!!, user.id, request.title.trim(),
            request.description.trim(), request.location.trim(), request.requirements.trim(),
            request.capacity, deadline.toString(), start.toString(), endsAt = request.endsAt, category = request.category, membersOnly = request.membersOnly).also { s.activities.add(it) }
    }

    fun edit(id: String, activityId: String, request: ActivityRequest): Activity = repository.change { s ->
        val user = actor(s, id, Role.CLUB_STAFF)
        val event = activity(s, activityId)
        requireRule(event.clubId == user.clubId, 403, "只能编辑本社团的活动")
        requireRule(event.status != ActivityStatus.CANCELLED, 409, "已取消的活动不能编辑")
        requireRule(request.title.trim().length in 2..100 && request.description.trim().length in 2..3000, 400, "标题或简介长度不正确")
        requireRule(request.location.trim().length in 2..200 && request.requirements.trim().length in 1..500, 400, "地点或参加条件不正确")
        requireRule(request.category in listOf("音乐", "运动", "学术", "艺术", "公益", "其他"), 400, "活动分类无效")
        val count = s.registrations.count { it.activityId == activityId }
        requireRule(request.capacity in 1..5000 && request.capacity >= count, 400, "人数上限不能少于已报名人数")
        requireRule(!request.membersOnly || s.registrations.filter { it.activityId == activityId }.all { r ->
            s.clubMemberships.any { it.studentId == r.studentId && it.clubId == event.clubId && it.status == MembershipStatus.APPROVED }
        }, 409, "已有报名学生不是本社团成员，不能设置为仅限社团成员")
        val deadline = parseDate(request.deadline)
        val start = parseDate(request.startsAt)
        requireRule(deadline.isAfter(now()) && start.isAfter(deadline), 400, "报名截止时间应晚于现在，活动时间应晚于截止时间")
        requireRule(request.endsAt.isBlank() || parseDate(request.endsAt).isAfter(start), 400, "结束时间应晚于开始时间")
        val updated = event.copy(title = request.title.trim(), description = request.description.trim(),
            location = request.location.trim(), requirements = request.requirements.trim(), capacity = request.capacity,
            deadline = deadline.toString(), startsAt = start.toString(), endsAt = request.endsAt,
            status = ActivityStatus.PENDING, reviewComment = "", reviewerId = null, updatedAt = now().toString(), category = request.category, membersOnly = request.membersOnly)
        s.activities[s.activities.indexOf(event)] = updated
        val changes = listOfNotNull(
            if (event.title != updated.title) "名称：${event.title} → ${updated.title}" else null,
            if (event.description != updated.description) "活动简介已修改" else null,
            if (event.requirements != updated.requirements) "参加条件已修改" else null,
            if (event.membersOnly != updated.membersOnly) "成员资格要求已修改" else null,
            if (event.location != updated.location) "地点：${event.location} → ${updated.location}" else null,
            if (event.startsAt != updated.startsAt) "开始时间：${event.startsAt} → ${updated.startsAt}" else null,
            if (event.endsAt != updated.endsAt) "结束时间：${event.endsAt.ifBlank { "未设置" }} → ${updated.endsAt.ifBlank { "未设置" }}" else null,
            if (event.deadline != updated.deadline) "报名截止时间已修改" else null,
            if (event.capacity != updated.capacity) "人数上限：${event.capacity} → ${updated.capacity}" else null,
            if (event.category != updated.category) "分类：${event.category} → ${updated.category}" else null
        ).joinToString("；").ifBlank { "重新提交审核" }
        logChange(s, event.id, id, changes)
        s.registrations.filter { it.activityId == event.id }.forEach { notify(s, it.studentId, "活动「${updated.title}」已修改并重新送审：$changes") }
        updated
    }

    fun reviewed(id: String): List<ActivityView> = repository.read { s ->
        actor(s, id, Role.TEACHER)
        s.activities.filter { it.status == ActivityStatus.APPROVED }.map { view(s, it) }
    }
    fun setEndTime(id: String, activityId: String, endsAt: String): Activity = repository.change { s ->
        actor(s, id, Role.TEACHER)
        val event = activity(s, activityId)
        requireRule(event.status == ActivityStatus.APPROVED, 409, "只能设置已通过活动的结束时间")
        requireRule(parseDate(endsAt).isAfter(parseDate(event.startsAt)), 400, "结束时间应晚于开始时间")
        val updated = event.copy(endsAt = endsAt, updatedAt = now().toString())
        s.activities[s.activities.indexOf(event)] = updated
        logChange(s, event.id, id, "教师设置结束时间：$endsAt")
        s.registrations.filter { it.activityId == event.id }.forEach { notify(s, it.studentId, "活动「${event.title}」的结束时间已更新为 $endsAt") }
        updated
    }
    fun allActivities(id: String): List<ActivityView> = repository.read { s ->
        actor(s, id, Role.ADMIN)
        s.activities.map { view(s, it) }
    }
    fun allParticipants(id: String, activityId: String): List<Participant> = repository.read { s ->
        actor(s, id, Role.ADMIN)
        activity(s, activityId)
        s.registrations.filter { it.activityId == activityId }.map { r ->
            Participant(s.users.first { it.id == r.studentId }.public(), r.createdAt)
        }
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
        recordReview(s, "活动", event.id, event.title, event.clubId, teacher, request)
        notify(s, event.creatorId, "活动「${event.title}」${if (request.approved) "已通过审核" else "未通过审核：${request.comment.trim()}"}")
        if (event.updatedAt != null) s.registrations.filter { it.activityId == event.id }.forEach { notify(s, it.studentId, "活动「${event.title}」重新审核${if (request.approved) "已通过" else "未通过"}") }
        updated
    }

    fun register(id: String, activityId: String): Registration {
        // Keep failed-attempt notifications in the same committed change as the eligibility check.
        val outcome = repository.change { s ->
            actor(s, id, Role.STUDENT)
            val event = s.activities.find { it.id == activityId }
            val reason = when {
                event == null -> "活动不存在"
                event.status != ActivityStatus.APPROVED -> "活动尚未发布或已取消"
                !now().isBefore(parseDate(event.deadline)) -> "报名时间已截止"
                s.registrations.any { it.activityId == activityId && it.studentId == id } -> "你已经报名此活动"
                event.membersOnly && s.clubMemberships.none { it.studentId == id && it.clubId == event.clubId && it.status == MembershipStatus.APPROVED } -> "该活动仅限所属社团成员参加，你尚未加入该社团"
                s.registrations.count { it.activityId == activityId } >= event.capacity -> "报名人数已满"
                else -> null
            }
            if (reason != null) {
                notify(s, id, "报名活动「${event?.title ?: "未知活动"}」失败：$reason。")
                Pair<Registration?, RuleViolation?>(null, RuleViolation(if (event == null) 404 else 409, reason))
            } else {
                val activity = event!!
                val registration = Registration(UUID.randomUUID().toString(), activityId, id, now().toString())
                s.registrations.add(registration)
                notify(s, id, "恭喜成功报名「${activity.title}」活动，请同学们注意活动地点并合理安排时间。")
                Pair<Registration?, RuleViolation?>(registration, null)
            }
        }
        outcome.second?.let { throw it }
        return outcome.first!!
    }
    fun registrations(id: String): List<RegistrationView> = repository.read { s ->
        actor(s, id, Role.STUDENT)
        s.registrations.filter { it.studentId == id }.map { RegistrationView(it, view(s, activity(s, it.activityId)), s.reminders.find { r -> r.registrationId == it.id }?.minutesBefore) }
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
        logChange(s, event.id, id, "活动已取消")
        s.registrations.filter { it.activityId == event.id }.forEach { notify(s, it.studentId, "活动「${event.title}」已取消") }
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
        requireRule(s.users.none { it.username.equals(username, true) } && s.accountApplications.none { it.status == AccountApplicationStatus.PENDING && it.nickname.equals(username, true) }, 409, "账号已存在或正在审核")
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
