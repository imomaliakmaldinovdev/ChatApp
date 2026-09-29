package com.imomali.chatapp.domain

object AuthValidation {
    fun name(value: String): Boolean = value.trim().length in 1..60
    fun email(value: String): Boolean = value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    fun password(value: String): Boolean = value.length >= 6
}
