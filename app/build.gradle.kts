plugins {
    kotlin("jvm")
    application
}

val gdxVersion = "1.12.1"

sourceSets {
    main {
        resources.srcDirs("src/main/assets")
    }
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    // Поддержка геймпадов
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-core:2.2.1")
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-desktop:2.2.1")
}

application {
    mainClass.set("com.example.dronerun.DesktopLauncherKt")
}

tasks.withType<JavaExec> {
    // Если папка существует в корне модуля — ставим её, иначе Gradle использует дефолт
    val assetsFolder = file("src/main/assets")
    if (assetsFolder.exists()) {
        workingDir = assetsFolder
    }
}
