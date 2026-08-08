package com.example.dronerun

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.MathUtils

class Obstacle(startX: Float) {
    val width = 80f
    val gap = 200f // Размер прохода для дрона

    var x = startX
    val speed = 250f
    var scored = false

    // Случайная высота нижнего препятствия
    private val bottomHeight = MathUtils.random(100f, 400f)

    // Прямоугольники для проверки столкновений
    val bottomBounds = Rectangle(x, 0f, width, bottomHeight)
    val topBounds = Rectangle(x, bottomHeight + gap, width, 720f - (bottomHeight + gap))

    fun update(delta: Float) {
        x -= speed * delta
        bottomBounds.x = x
        topBounds.x = x
    }

    fun draw(shapeRenderer: ShapeRenderer) {
        // Нижняя труба/здание
        shapeRenderer.rect(bottomBounds.x, bottomBounds.y, bottomBounds.width, bottomBounds.height)
        // Верхняя труба/здание
        shapeRenderer.rect(topBounds.x, topBounds.y, topBounds.width, topBounds.height)
    }
}