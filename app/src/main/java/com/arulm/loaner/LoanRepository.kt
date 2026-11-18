package com.arulm.loaner

import android.content.Context
import java.io.File

class LoanRepository(context: Context) {
    private val file = File(context.filesDir, "loaner-data.json")

    fun load(): AppData = if (!file.exists()) AppData() else runCatching {
        appDataFromJson(file.readText())
    }.getOrElse { AppData() }

    fun save(data: AppData) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(data.toJson().toString(2))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}

