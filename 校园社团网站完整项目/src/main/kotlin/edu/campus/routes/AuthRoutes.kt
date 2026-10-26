package edu.campus.routes

import edu.campus.model.*
import edu.campus.service.CampusService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.authRoutes(service: CampusService) {
    get("/api/config") {
        call.respond(PublicConfig(Role.entries.none { System.getenv("BOOTSTRAP_${it.name}_PASSWORD") != null }))
    }
    get("/api/health") { call.respond(Message("ok")) }
    post("/api/auth/login") {
        val user = service.login(call.receive())
        call.sessions.set(LoginSession(user.id))
        call.respond(user)
    }
    post("/api/auth/logout") { call.sessions.clear<LoginSession>(); call.respond(Message("已退出")) }
    get("/api/auth/me") { call.respond(service.current(call.userId())) }
    get("/api/clubs") { call.respond(service.clubs()) }
    get("/api/activities") { call.respond(service.published(call.request.queryParameters["q"].orEmpty())) }
}
