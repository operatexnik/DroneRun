package com.example.dronerun

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20

class DroneRunGame : Game() {
    override fun create() {
        // Переключаем игру на наш игровой экран
        setScreen(GameScreen())
    }

    override fun render() {
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        super.render()
    }
}