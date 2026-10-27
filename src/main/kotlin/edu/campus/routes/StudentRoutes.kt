package edu.campus.routes

import edu.campus.service.CampusService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.studentRoutes(service: CampusService) {
    post("/api/activities/{id}/registrations") {
        call.respond(HttpStatusCode.Created, service.register(call.userId(), call.recordId()))
    }
    get("/api/student/registrations") { call.respond(service.registrations(call.userId())) }
}
