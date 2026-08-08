package com.example.dronerun

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import java.io.File

fun main() {
    // Автоматически ищем папку с ассетами от текущего места запуска
    val possiblePaths = listOf(
        "src/main/assets",
        "app/src/main/assets",
        "assets",
        "../src/main/assets",
        "../app/src/main/assets"
    )

    for (path in possiblePaths) {
        val folder = File(path)
        if (folder.exists() && File(folder, "bg.png").exists()) {
            System.setProperty("user.dir", folder.absolutePath)
            println("Working directory set to: ${folder.absolutePath}")
            break
        }
    }

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("DroneRun")
        setWindowedMode(1280, 720)
        setResizable(false)
        setForegroundFPS(60)
    }

    Lwjgl3Application(DroneRunGame(), config)
}