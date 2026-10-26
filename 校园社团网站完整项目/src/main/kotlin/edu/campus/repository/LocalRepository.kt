package edu.campus.repository

import edu.campus.model.*
import edu.campus.security.Passwords
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.time.LocalDateTime
import java.time.ZoneId

/** One process owns one file. Keep this directory outside public web resources. */
class LocalRepository(
    private val file: Path,
    private val initialPasswords: Map<Role, String> = Role.entries.mapNotNull { role ->
        System.getenv("BOOTSTRAP_${role.name}_PASSWORD")?.let { role to it }
    }.toMap()
) {
    private val json = Json { prettyPrint = true }
    private var state: State

    init {
        Files.createDirectories(file.toAbsolutePath().parent)
        // A malformed existing file fails visibly; it is never silently overwritten.
        state = if (Files.exists(file)) json.decodeFromString<State>(Files.readString(file)) else seed()
        if (!Files.exists(file)) save(state)
    }

    @Synchronized fun <T> read(block: (State) -> T): T = block(state)

    @Synchronized fun <T> change(block: (State) -> T): T {
        val next = State(state.users.toMutableList(), state.clubs.toMutableList(),
            state.activities.toMutableList(), state.registrations.toMutableList())
        val result = block(next)
        save(next)
        state = next
        return result
    }

    private fun save(next: State) {
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, json.encodeToString(next))
        try { Files.move(temp, file, REPLACE_EXISTING, ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp, file, REPLACE_EXISTING) }
    }

    private fun seed(): State {
        fun hash(role: Role): String {
            val password = initialPasswords[role] ?: "demo1234"
            require(initialPasswords[role] == null || password.length >= 12) { "Server bootstrap passwords must have at least 12 characters" }
            return Passwords.hash(password)
        }
        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        return State(
            users = mutableListOf(
                User("u-student", "student", "林同学", hash(Role.STUDENT), Role.STUDENT),
                User("u-club", "club", "社团负责人", hash(Role.CLUB_STAFF), Role.CLUB_STAFF, "guitar"),
                User("u-teacher", "teacher", "审核老师", hash(Role.TEACHER), Role.TEACHER),
                User("u-admin", "admin", "系统管理员", hash(Role.ADMIN), Role.ADMIN)
            ),
            clubs = mutableListOf(Club("guitar", "吉他社"), Club("acm", "ACM 协会"), Club("basketball", "篮球社")),
            activities = mutableListOf(
                Activity("a-guitar", "guitar", "u-club", "草坪上的吉他交流会",
                    "带上喜欢的歌曲，和社员一起交流弹奏。欢迎零基础同学参加。", "犀浦校区 · 学生活动中心",
                    "本校在读学生，可自带乐器", 30, now.plusDays(6).withSecond(0).withNano(0).toString(),
                    now.plusDays(7).withSecond(0).withNano(0).toString(), ActivityStatus.APPROVED,
                    "同意发布", "u-teacher")
            )
        )
    }
}
