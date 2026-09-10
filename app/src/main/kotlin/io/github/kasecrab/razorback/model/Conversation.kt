package io.github.kasecrab.razorback.model

class Conversation(
    val id: String,
    var title: String,
    var model: String?,
    val createdAt: Long,
    var updatedAt: Long,
    var pinned: Boolean = false,
    var archived: Boolean = false,
)
