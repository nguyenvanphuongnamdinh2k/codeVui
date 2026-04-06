package com.example.codevui.util

import android.util.Log

/**
 * Logger utility — wraps Android Log with consistent format.
 *
 * Format:  "van.phuong.{ClassName}  {methodName}: {message}"
 * Example: "van.phuong.BrowseScreen  onCreate: navigating to root"
 *
 * Usage:
 *   class MyClass {
 *       private val log = Logger("MyClass")
 *
 *       fun doSomething() {
 *           log.d("doing work")          // → van.phuong.MyClass  doSomething: doing work
 *           log.e("failed", throwable)   // → van.phuong.MyClass  doSomething: failed
 *       }
 *   }
 */
@Suppress("unused")
class Logger(private val className: String) {

    private val tag: String get() = "van.phuong.$className"

    private fun log(
        level: Int,
        message: String,
        throwable: Throwable? = null,
        block: (String, String, Throwable?) -> Int
    ): Int {
        val result = findCaller()
        val callerMethod = result.first
        val callerLine = result.second
        val formattedMsg = "$callerMethod:$callerLine  $message"
        return if (throwable != null) {
            block(tag, formattedMsg, throwable)
        } else {
            block(tag, formattedMsg, null)
        }
    }

    private fun findCaller(): Pair<String, Int> {
        val stack = Thread.currentThread().stackTrace
        // stack[0] = getStackTrace
        // stack[1] = findCaller
        // stack[2] = log / log(...)
        // stack[3] = caller (the actual method calling log.d/e/etc)
        for (i in 4 until stack.size) {
            val element = stack[i]
            if (element.className != Logger::class.java.name &&
                !element.className.startsWith("android.") &&
                !element.className.startsWith("java.") &&
                !element.className.startsWith("kotlin.")) {
                return Pair(element.methodName, element.lineNumber)
            }
        }
        return Pair("unknown", -1)
    }

    fun v(message: String) = log(Log.VERBOSE, message, null) { t, m, _ ->
        Log.v(t, m)
    }

    fun d(message: String) = log(Log.DEBUG, message, null) { t, m, _ ->
        Log.d(t, m)
    }

    fun i(message: String) = log(Log.INFO, message, null) { t, m, _ ->
        Log.i(t, m)
    }

    fun w(message: String) = log(Log.WARN, message, null) { t, m, _ ->
        Log.w(t, m)
    }

    fun e(message: String, throwable: Throwable? = null) =
        log(Log.ERROR, message, throwable) { t, m, tr ->
            if (tr != null) Log.e(t, m, tr) else Log.e(t, m)
        }

    fun wt(message: String, throwable: Throwable) =
        log(Log.WARN, message, throwable) { t, m, tr ->
            if (tr != null) Log.wtf(t, m, tr) else Log.wtf(t, m)
        }

    companion object {
        /** Convenience: create logger with simple class name */
        inline fun <reified T : Any> forClass(): Logger =
            Logger(T::class.java.simpleName)
    }
}
