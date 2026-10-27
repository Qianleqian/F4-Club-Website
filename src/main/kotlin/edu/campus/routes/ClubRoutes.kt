package edu.campus.routes

import edu.campus.service.CampusService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.clubRoutes(service: CampusService) {
    get("/api/club/activities") { call.respond(service.myActivities(call.userId())) }
    post("/api/club/activities") { call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive())) }
    get("/api/club/activities/{id}/participants") { call.respond(service.participants(call.userId(), call.recordId())) }
    post("/api/club/activities/{id}/cancel") { call.respond(service.cancel(call.userId(), call.recordId())) }
}
