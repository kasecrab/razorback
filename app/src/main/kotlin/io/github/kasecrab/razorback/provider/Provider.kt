package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.model.ModelInfo

/**
 * A chat backend. [streamChat] blocks on the calling thread and reports through the
 * listener; the handle cancels from anywhere. Adding a vendor means one implementation.
 */
interface Provider {
    val id: String

    fun streamChat(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit)

    fun listModels(): List<ModelInfo>
}
