package com.ubermax.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM puros del intérprete de comandos de voz.
 */
class VoiceCommandParserTest {

    @Test
    fun `reconoce aceptar y variantes`() {
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("aceptar"))
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("Aceptá"))
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("sí"))
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("Accept"))
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("Acepto el viaje"))
    }

    @Test
    fun `reconoce rechazar y variantes`() {
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("rechazar"))
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("no"))
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("cancelar"))
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("decline"))
    }

    @Test
    fun `reconoce siguiente`() {
        assertEquals(VoiceCommand.NEXT, VoiceCommandParser.parse("siguiente"))
        assertEquals(VoiceCommand.NEXT, VoiceCommandParser.parse("esperar"))
        assertEquals(VoiceCommand.NEXT, VoiceCommandParser.parse("wait"))
    }

    @Test
    fun `texto no reconocido devuelve NONE`() {
        assertEquals(VoiceCommand.NONE, VoiceCommandParser.parse("hola qué tal"))
        assertEquals(VoiceCommand.NONE, VoiceCommandParser.parse(""))
        assertEquals(VoiceCommand.NONE, VoiceCommandParser.parse("   "))
        assertEquals(VoiceCommand.NONE, VoiceCommandParser.parse(null))
    }

    @Test
    fun `una frase con la palabra clave se reconoce`() {
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("no, gracias"))
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("dale, acepta"))
    }
}
