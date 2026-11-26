package edu.campus.routes

import edu.campus.service.CampusService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.clubRoutes(service: CampusService) {
    get("/api/club/membership-applications") { call.respond(service.pendingMemberships(call.userId())) }
    post("/api/club/membership-applications/{id}/review") { call.respond(service.reviewMembership(call.userId(), call.recordId(), call.receive())) }
    get("/api/club/reviews") { call.respond(service.reviewHistory(call.userId())) }
    get("/api/club/posts") { call.respond(service.clubPosts(call.userId())) }
    post("/api/club/posts") { call.respond(HttpStatusCode.Created, service.savePost(call.userId(), null, call.receive())) }
    put("/api/club/posts/{id}") { call.respond(service.savePost(call.userId(), call.recordId(), call.receive())) }
    get("/api/club/applications") { call.respond(service.clubApplications(call.userId())) }
    post("/api/club/applications") { call.respond(HttpStatusCode.Created, service.applyClub(call.userId(), call.receive())) }
    get("/api/club/activities") { call.respond(service.myActivities(call.userId())) }
    post("/api/club/activities") { call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive())) }
    put("/api/club/activities/{id}") { call.respond(service.edit(call.userId(), call.recordId(), call.receive())) }
    get("/api/club/activities/{id}/participants") { call.respond(service.participants(call.userId(), call.recordId())) }
    post("/api/club/activities/{id}/cancel") { call.respond(service.cancel(call.userId(), call.recordId())) }
}
