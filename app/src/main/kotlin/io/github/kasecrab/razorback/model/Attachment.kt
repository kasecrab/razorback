package io.github.kasecrab.razorback.model

enum class AttachmentKind { IMAGE, TEXT }

/** A file kept under the app's private storage and referenced from a message. */
class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val mime: String,
    /** Absolute path inside filesDir. */
    val path: String,
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0,
)
