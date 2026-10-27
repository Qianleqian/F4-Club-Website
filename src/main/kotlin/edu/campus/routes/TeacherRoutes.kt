package edu.campus.routes

import edu.campus.service.CampusService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.teacherRoutes(service: CampusService) {
    get("/api/teacher/pending") { call.respond(service.pending(call.userId())) }
    post("/api/teacher/activities/{id}/review") {
        call.respond(service.review(call.userId(), call.recordId(), call.receive()))
    }
}
