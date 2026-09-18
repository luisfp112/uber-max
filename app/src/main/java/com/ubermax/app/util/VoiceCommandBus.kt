package com.ubermax.app.util

import com.ubermax.app.domain.model.VoiceCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus en proceso para comandos de voz. El [com.ubermax.app.service.VoiceCommandService]
 * publica lo reconocido y [com.ubermax.app.service.UberAccessibilityService] lo consume.
 *
 * Es un singleton de proceso: no persiste ni cruza procesos.
 */
object VoiceCommandBus {

    private val _commands = MutableSharedFlow<VoiceCommand>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val commands = _commands.asSharedFlow()

    fun emit(command: VoiceCommand) {
        if (command == VoiceCommand.NONE) return
        _commands.tryEmit(command)
    }
}
