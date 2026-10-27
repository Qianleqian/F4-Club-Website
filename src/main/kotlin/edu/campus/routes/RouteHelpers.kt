package edu.campus.routes

import edu.campus.model.LoginSession
import edu.campus.service.RuleViolation
import io.ktor.server.application.*
import io.ktor.server.sessions.*

internal fun ApplicationCall.userId(): String = sessions.get<LoginSession>()?.userId
    ?: throw RuleViolation(401, "请先登录")
internal fun ApplicationCall.recordId(): String = parameters["id"] ?: throw RuleViolation(400, "缺少编号")
