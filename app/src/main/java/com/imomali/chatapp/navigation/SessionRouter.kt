package com.imomali.chatapp.navigation
enum class Route { WELCOME, CHATS, PROFILE, CONVERSATION }
object SessionRouter {
    fun resolve(requested: Route, userId: String?): Route = when {
        userId.isNullOrBlank() -> Route.WELCOME
        requested == Route.WELCOME -> Route.CHATS
        else -> requested
    }
}
