package edu.campus.routes

import edu.campus.model.EnabledRequest
import edu.campus.service.CampusService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.adminRoutes(service: CampusService) {
    get("/api/admin/users") { call.respond(service.users(call.userId())) }
    post("/api/admin/users") { call.respond(HttpStatusCode.Created, service.createUser(call.userId(), call.receive())) }
    patch("/api/admin/users/{id}/enabled") {
        val request = call.receive<EnabledRequest>()
        call.respond(service.setEnabled(call.userId(), call.recordId(), request.enabled))
    }
}
