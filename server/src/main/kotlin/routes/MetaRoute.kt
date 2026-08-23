package dev.juanvega.routes

import dev.juanvega.Resources
import dev.juanvega.model.UserInfoResponse
import dev.juanvega.source.BodyMeasurementSource
import dev.juanvega.source.ExerciseTemplateSource
import dev.juanvega.source.RoutineFolderSource
import dev.juanvega.source.RoutineSource
import dev.juanvega.source.UserInfoSource
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Routes for API domains whose data sources are not wired yet (routines, exercise
 * templates, measurements, …). While a domain's source reports `isAvailable == false`
 * its routes respond `503 Service Unavailable`; once a real source is wired in,
 * the same handlers serve it without changes.
 */
fun Route.metaRoutes(
    exerciseTemplates: ExerciseTemplateSource,
    routineFolders: RoutineFolderSource,
    routines: RoutineSource,
    bodyMeasurements: BodyMeasurementSource,
    userInfo: UserInfoSource,
) {
    get<Resources.ExerciseTemplates> {
        if (!exerciseTemplates.isAvailable) call.unavailable("exercise template")
        else call.respond(ExerciseTemplatesResponse(exerciseTemplates.exerciseTemplates()))
    }

    get<Resources.ExerciseTemplateById> { resource ->
        if (!exerciseTemplates.isAvailable) {
            call.unavailable("exercise template")
            return@get
        }
        val template = exerciseTemplates.exerciseTemplate(resource.id)
        if (template == null) {
            call.notFound("Exercise template ${resource.id} not found")
            return@get
        }
        call.respond(template)
    }

    get<Resources.RoutineFolders> {
        if (!routineFolders.isAvailable) call.unavailable("routine folder")
        else call.respond(RoutineFoldersResponse(routineFolders.routineFolders()))
    }

    get<Resources.RoutineFolderById> { resource ->
        if (!routineFolders.isAvailable) {
            call.unavailable("routine folder")
            return@get
        }
        val folder = routineFolders.routineFolder(resource.id)
        if (folder == null) {
            call.notFound("Routine folder ${resource.id} not found")
            return@get
        }
        call.respond(folder)
    }

    get<Resources.Routines> {
        if (!routines.isAvailable) call.unavailable("routine")
        else call.respond(RoutinesResponse(routines.routines()))
    }

    get<Resources.RoutineById> { resource ->
        if (!routines.isAvailable) {
            call.unavailable("routine")
            return@get
        }
        val routine = routines.routine(resource.id)
        if (routine == null) {
            call.notFound("Routine ${resource.id} not found")
            return@get
        }
        call.respond(SingleRoutineResponse(routine))
    }

    get<Resources.BodyMeasurements> {
        if (!bodyMeasurements.isAvailable) call.unavailable("body measurement")
        else call.respond(BodyMeasurementsResponse(bodyMeasurements.bodyMeasurements()))
    }

    get<Resources.BodyMeasurementByDate> { resource ->
        if (!bodyMeasurements.isAvailable) {
            call.unavailable("body measurement")
            return@get
        }
        val measurement = bodyMeasurements.bodyMeasurements().find { it.date == resource.date }
        if (measurement == null) {
            call.notFound("Body measurement for ${resource.date} not found")
            return@get
        }
        call.respond(measurement)
    }

    get<Resources.UserInfo> {
        if (!userInfo.isAvailable) call.unavailable("user info")
        else call.respond(UserInfoResponse(userInfo.userInfo()))
    }
}
