package com.ubermax.app.util

import android.util.Log

/**
 * Log wrapper que NO lanza excepciones en tests unitarios JVM puros.
 *
 * En la JVM de prueba, el android.jar "mockable" hace que cualquier llamada a
 * `android.util.Log` lance `RuntimeException("Method ... not mocked")`.
 * Este wrapper neutraliza esa excepción para que [OfferParser], [ActionExecutor]
 * y demás lógica pura puedan validarse con JUnit sin Robolectric ni dispositivo.
 * En el dispositivo real, el comportamiento es idéntico a usar Log directamente.
 */
object Logs {

    fun v(tag: String, msg: String) { runCatching { Log.v(tag, msg) } }
    fun d(tag: String, msg: String) { runCatching { Log.d(tag, msg) } }
    fun i(tag: String, msg: String) { runCatching { Log.i(tag, msg) } }
    fun w(tag: String, msg: String) { runCatching { Log.w(tag, msg) } }
    fun e(tag: String, msg: String) { runCatching { Log.e(tag, msg) } }
    fun e(tag: String, msg: String, tr: Throwable) { runCatching { Log.e(tag, msg, tr) } }
}