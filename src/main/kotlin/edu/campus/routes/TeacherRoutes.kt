package edu.campus.routes

import edu.campus.service.CampusService
import edu.campus.model.EndTimeRequest
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.teacherRoutes(service: CampusService) {
    get("/api/teacher/reviews") { call.respond(service.reviewHistory(call.userId())) }
    get("/api/teacher/posts") { call.respond(service.pendingPosts(call.userId())) }
    post("/api/teacher/posts/{id}/review") { call.respond(service.reviewPost(call.userId(), call.recordId(), call.receive())) }
    get("/api/teacher/club-applications") { call.respond(service.clubApplications(call.userId())) }
    get("/api/teacher/staff") { call.respond(service.staffList(call.userId())) }
    post("/api/teacher/club-applications/{id}/review") { call.respond(service.reviewClub(call.userId(), call.recordId(), call.receive())) }
    get("/api/teacher/approved") { call.respond(service.reviewed(call.userId())) }
    patch("/api/teacher/activities/{id}/end-time") {
        call.respond(service.setEndTime(call.userId(), call.recordId(), call.receive<EndTimeRequest>().endsAt))
    }
    get("/api/teacher/pending") { call.respond(service.pending(call.userId())) }
    post("/api/teacher/activities/{id}/review") {
        call.respond(service.review(call.userId(), call.recordId(), call.receive()))
    }
}
