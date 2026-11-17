package edu.campus.routes

import edu.campus.service.CampusService
import edu.campus.model.ReminderRequest
import edu.campus.model.Message
import io.ktor.server.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.studentRoutes(service: CampusService) {
    post("/api/activities/{id}/registrations") {
        call.respond(HttpStatusCode.Created, service.register(call.userId(), call.recordId()))
    }
    patch("/api/student/registrations/{id}/reminder") {
        call.respond(service.setReminder(call.userId(), call.recordId(), call.receive<ReminderRequest>().minutesBefore) ?: Message("提醒已关闭"))
    }
    get("/api/student/registrations") { call.respond(service.registrations(call.userId())) }
}
