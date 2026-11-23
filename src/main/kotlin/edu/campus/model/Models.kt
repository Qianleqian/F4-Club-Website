package edu.campus.model

import kotlinx.serialization.Serializable

@Serializable enum class Role { STUDENT, CLUB_STAFF, TEACHER, ADMIN }
@Serializable enum class ActivityStatus { PENDING, APPROVED, REJECTED, CANCELLED }

@Serializable
data class User(
    val id: String, val username: String, val name: String,
    val passwordHash: String, val role: Role,
    val clubId: String? = null, val enabled: Boolean = true
) {
    fun public() = PublicUser(id, username, name, role, clubId, enabled)
}

@Serializable
data class PublicUser(
    val id: String, val username: String, val name: String, val role: Role,
    val clubId: String? = null, val enabled: Boolean = true
)
@Serializable data class Club(val id: String, val name: String)

// Dates are ISO local date-times in Asia/Shanghai, e.g. 2026-11-01T18:00.
@Serializable
data class Activity(
    val id: String, val clubId: String, val creatorId: String,
    val title: String, val description: String, val location: String,
    val requirements: String, val capacity: Int,
    val deadline: String, val startsAt: String,
    val status: ActivityStatus = ActivityStatus.PENDING,
    val reviewComment: String = "", val reviewerId: String? = null,
    val endsAt: String = "", val updatedAt: String? = null, val category: String = "其他"
)
@Serializable
data class Registration(val id: String, val activityId: String, val studentId: String, val createdAt: String)
@Serializable
data class ActivityView(val activity: Activity, val clubName: String, val registeredCount: Int, val creatorName: String = "")
@Serializable
data class RegistrationView(val registration: Registration, val event: ActivityView, val reminderMinutes: Int? = null)
@Serializable
data class Participant(val student: PublicUser, val registeredAt: String)

@Serializable
data class State(
    val users: MutableList<User> = mutableListOf(),
    val clubs: MutableList<Club> = mutableListOf(),
    val activities: MutableList<Activity> = mutableListOf(),
    val registrations: MutableList<Registration> = mutableListOf(),
    val notifications: MutableList<Notification> = mutableListOf(),
    val history: MutableList<ActivityChange> = mutableListOf(),
    val clubApplications: MutableList<ClubApplication> = mutableListOf(),
    val reminders: MutableList<Reminder> = mutableListOf(),
    val posts: MutableList<ClubPost> = mutableListOf(),
    val reviews: MutableList<ReviewRecord> = mutableListOf(),
    val accountApplications: MutableList<AccountApplication> = mutableListOf()
)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class LoginSession(val userId: String)
@Serializable
data class ActivityRequest(
    val title: String, val description: String, val location: String,
    val requirements: String, val capacity: Int, val deadline: String, val startsAt: String,
    val endsAt: String = "", val category: String = "其他"
)
@Serializable data class ReviewRequest(val approved: Boolean, val comment: String = "")
@Serializable
data class CreateUserRequest(
    val username: String, val name: String, val password: String, val role: Role,
    val clubId: String? = null
)
@Serializable data class EnabledRequest(val enabled: Boolean)
@Serializable data class Message(val message: String)
@Serializable data class PublicConfig(val demoLoginEnabled: Boolean)

@Serializable data class EndTimeRequest(val endsAt: String)

@Serializable data class Notification(val id: String, val userId: String, val text: String, val createdAt: String, val read: Boolean = false)
@Serializable data class ActivityChange(val id: String, val activityId: String, val actorId: String, val summary: String, val createdAt: String)
@Serializable data class ClubApplication(val id: String, val name: String, val description: String, val applicantId: String,
    val status: ActivityStatus = ActivityStatus.PENDING, val comment: String = "", val createdAt: String)
@Serializable data class ClubApplicationRequest(val name: String, val description: String)

@Serializable data class Reminder(val registrationId: String, val minutesBefore: Int, val sentFor: String? = null)
@Serializable data class ReminderRequest(val minutesBefore: Int?)
@Serializable data class ClubPost(val id: String, val clubId: String, val creatorId: String, val title: String, val content: String,
    val createdAt: String, val status: ActivityStatus = ActivityStatus.PENDING, val reviewComment: String = "")
@Serializable data class ClubPostRequest(val title: String, val content: String)
@Serializable data class PostView(val post: ClubPost, val clubName: String, val creatorName: String)
@Serializable data class ReviewRecord(val id: String, val kind: String, val targetId: String, val targetTitle: String,
    val clubId: String, val reviewerId: String, val reviewerName: String, val approved: Boolean, val comment: String, val reviewedAt: String)

@Serializable enum class AccountApplicationStatus { PENDING, APPROVED, REJECTED }
@Serializable data class RegisterRequest(val nickname: String, val password: String, val role: Role, val clubId: String? = null)
@Serializable data class RegisterResult(val message: String, val requiresApproval: Boolean)
@Serializable data class AccountApplication(val id: String, val nickname: String, val passwordHash: String,
    val role: Role, val clubId: String? = null, val createdAt: String,
    val status: AccountApplicationStatus = AccountApplicationStatus.PENDING,
    val reviewerId: String? = null, val reviewComment: String = "", val reviewedAt: String? = null)
@Serializable data class AccountApplicationView(val id: String, val nickname: String, val role: Role,
    val clubId: String? = null, val clubName: String? = null, val createdAt: String)
@Serializable data class AccountDecisionRequest(val approved: Boolean, val comment: String = "")
