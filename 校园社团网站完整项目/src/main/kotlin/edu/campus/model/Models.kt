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
    val reviewComment: String = "", val reviewerId: String? = null
)
@Serializable
data class Registration(val id: String, val activityId: String, val studentId: String, val createdAt: String)
@Serializable
data class ActivityView(val activity: Activity, val clubName: String, val registeredCount: Int)
@Serializable
data class RegistrationView(val registration: Registration, val event: ActivityView)
@Serializable
data class Participant(val student: PublicUser, val registeredAt: String)

@Serializable
data class State(
    val users: MutableList<User> = mutableListOf(),
    val clubs: MutableList<Club> = mutableListOf(),
    val activities: MutableList<Activity> = mutableListOf(),
    val registrations: MutableList<Registration> = mutableListOf()
)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class LoginSession(val userId: String)
@Serializable
data class ActivityRequest(
    val title: String, val description: String, val location: String,
    val requirements: String, val capacity: Int, val deadline: String, val startsAt: String
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
