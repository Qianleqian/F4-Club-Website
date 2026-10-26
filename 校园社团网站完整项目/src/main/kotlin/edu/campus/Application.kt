package edu.campus

import edu.campus.model.*
import edu.campus.repository.LocalRepository
import edu.campus.routes.*
import edu.campus.service.CampusService
import edu.campus.service.RuleViolation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.SecureRandom

fun main() {
    val host = System.getenv("HOST") ?: "127.0.0.1"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = host, port = port) { campusModule() }.start(wait = true)
}

fun Application.campusModule(
    repository: LocalRepository = LocalRepository(Path.of(System.getenv("DATA_FILE") ?: "data/state.json"))
) {
    val service = CampusService(repository)
    install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
    install(Sessions) {
        cookie<LoginSession>("campus_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            // Local-development secret: restarting the server invalidates old sessions.
            transform(SessionTransportTransformerMessageAuthentication(ByteArray(32).also { SecureRandom().nextBytes(it) }))
        }
    }
    install(StatusPages) {
        exception<RuleViolation> { call, error -> call.respond(HttpStatusCode.fromValue(error.status), Message(error.message)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Message("请求格式不正确，请检查输入")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Message("请求字段不完整或格式错误")) }
        exception<Throwable> { call, error ->
            call.application.log.error("Unhandled request failure", error)
            call.respond(HttpStatusCode.InternalServerError, Message("服务器处理失败，请查看服务端日志"))
        }
    }
    routing {
        authRoutes(service)
        studentRoutes(service)
        clubRoutes(service)
        teacherRoutes(service)
        adminRoutes(service)
        staticResources("/", "static", index = "index.html")
    }
}
