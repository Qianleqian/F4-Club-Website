package edu.campus.routes

import edu.campus.model.EnabledRequest
import edu.campus.model.AccountDecisionRequest
import edu.campus.service.CampusService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.adminRoutes(service: CampusService) {
    get("/api/admin/account-applications") { call.respond(service.accountApplications(call.userId())) }
    post("/api/admin/account-applications/{id}/decision") {
        call.respond(service.decideAccountApplication(call.userId(), call.recordId(), call.receive<AccountDecisionRequest>()))
    }
    get("/api/admin/reviews") { call.respond(service.reviewHistory(call.userId())) }
    get("/api/admin/staff") { call.respond(service.staffList(call.userId())) }
    get("/api/admin/activities") { call.respond(service.allActivities(call.userId())) }
    get("/api/admin/activities/{id}/participants") { call.respond(service.allParticipants(call.userId(), call.recordId())) }
    get("/api/admin/users") { call.respond(service.users(call.userId())) }
    post("/api/admin/users") { call.respond(HttpStatusCode.Created, service.createUser(call.userId(), call.receive())) }
    patch("/api/admin/users/{id}/enabled") {
        val request = call.receive<EnabledRequest>()
        call.respond(service.setEnabled(call.userId(), call.recordId(), request.enabled))
    }
}
