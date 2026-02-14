package com.gilbertohdz.sdk

@OptIn(ExperimentalJsExport::class)
@JsExport
actual fun platform() = "JavaScript"

@OptIn(ExperimentalJsExport::class)
@JsExport
class Greeting {
    fun greet(): String = "Hello, JavaScript!"
}
