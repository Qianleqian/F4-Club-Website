package edu.campus

import edu.campus.model.*
import edu.campus.repository.LocalRepository
import edu.campus.service.*
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class ServerBootstrapTest {
    @TempDir lateinit var directory: Path

    @Test fun `server passwords replace demo passwords on first run and survive restart`() {
        val path = directory.resolve("state.json")
        val passwords = Role.entries.associateWith { "server-password-${it.name}-1234" }
        val service = CampusService(LocalRepository(path, passwords))
        val names = mapOf(Role.STUDENT to "student", Role.CLUB_STAFF to "club", Role.TEACHER to "teacher", Role.ADMIN to "admin")
        for ((role, name) in names) {
            assertEquals(role, service.login(LoginRequest(name, passwords.getValue(role))).role)
            assertEquals(401, assertFailsWith<RuleViolation> { service.login(LoginRequest(name, "demo1234")) }.status)
        }
        val restarted = CampusService(LocalRepository(path, Role.entries.associateWith { "different-password-1234" }))
        assertEquals(Role.ADMIN, restarted.login(LoginRequest("admin", passwords.getValue(Role.ADMIN))).role)
    }
}
