package edu.campus

import edu.campus.model.*
import edu.campus.repository.LocalRepository
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.*

class ApplicationTest {
    @TempDir lateinit var directory: Path

    @Test fun `four role HTTP workflow checks sessions permissions and persistence`() = testApplication {
        application { campusModule(LocalRepository(directory.resolve("state.json"))) }
        val browser = createClient { install(ContentNegotiation) { json() }; install(HttpCookies) }
        suspend fun login(username: String) {
            assertEquals(HttpStatusCode.OK, browser.post("/api/auth/login") {
                contentType(ContentType.Application.Json); setBody(LoginRequest(username, "demo1234"))
            }.status)
        }
        assertEquals(HttpStatusCode.OK, browser.get("/").status)
        assertEquals(HttpStatusCode.OK, browser.get("/app.js").status)
        assertEquals(HttpStatusCode.Unauthorized, browser.get("/api/auth/me").status)
        assertEquals(HttpStatusCode.Unauthorized, browser.post("/api/activities/a-guitar/registrations").status)
        login("club")
        val created = browser.post("/api/club/activities") {
            contentType(ContentType.Application.Json)
            setBody(ActivityRequest("HTTP 联调活动", "端到端联调", "图书馆", "不限", 2, "2099-01-01T12:00", "2099-01-02T12:00"))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val event = created.body<Activity>()
        assertFalse(browser.get("/api/activities").body<List<ActivityView>>().any { it.activity.id == event.id })
        login("teacher")
        assertTrue(browser.get("/api/teacher/pending").body<List<ActivityView>>().any { it.activity.id == event.id })
        assertEquals(HttpStatusCode.OK, browser.post("/api/teacher/activities/${event.id}/review") {
            contentType(ContentType.Application.Json); setBody(ReviewRequest(true, "同意"))
        }.status)
        login("student")
        assertEquals(HttpStatusCode.Forbidden, browser.get("/api/admin/users").status)
        assertEquals(HttpStatusCode.Created, browser.post("/api/activities/${event.id}/registrations").status)
        assertEquals(HttpStatusCode.Conflict, browser.post("/api/activities/${event.id}/registrations").status)
        assertEquals(1, browser.get("/api/student/registrations").body<List<RegistrationView>>().size)
        val student = browser.get("/api/auth/me").body<PublicUser>()
        login("admin")
        assertFalse(browser.get("/api/admin/users").body<String>().contains("passwordHash"))
        assertEquals(HttpStatusCode.OK, browser.patch("/api/admin/users/${student.id}/enabled") {
            contentType(ContentType.Application.Json); setBody(EnabledRequest(false))
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, browser.post("/api/auth/login") {
            contentType(ContentType.Application.Json); setBody(LoginRequest("student", "demo1234"))
        }.status)
        assertEquals(1, LocalRepository(directory.resolve("state.json")).read { it.registrations.size })
    }
}
