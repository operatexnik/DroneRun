package com.example.dronerun

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.controllers.Controllers
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Array as GdxArray

class GameScreen : ScreenAdapter() {
    private val batch = SpriteBatch()
    private val font = BitmapFont().apply {
        data.setScale(2f)
    }

    // --- Звук ---
    private val propellerSound: Music? = try {
        if (Gdx.files.internal("propeller.mp3").exists()) {
            Gdx.audio.newMusic(Gdx.files.internal("propeller.mp3")).apply {
                isLooping = true
                volume = 0.5f
            }
        } else null
    } catch (e: Exception) { null }

    // --- Текстуры ---
    private val bgTexture = Texture("stone.png").apply {
        setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
    }

    private val amethystTexture = try {
        Texture("amethyst_shard.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { null }

    private val topBaseTex = Texture("pointed_dripstone_down_base.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val topMiddleTex = Texture("pointed_dripstone_down_middle.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val topTipTex = Texture("pointed_dripstone_down_tip.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }

    private val bottomBaseTex = Texture("pointed_dripstone_up_base.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val bottomMiddleTex = Texture("pointed_dripstone_up_middle.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val bottomTipTex = Texture("pointed_dripstone_up_tip.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }

    private var bgScrollTimer = 0f

    // --- Анимация дрона ---
    private val droneBounds = Rectangle(200f, 360f, 100f, 50f)

    private val droneOnTexture = Texture("drone_on.png").apply {
        setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
    }
    private val droneOffTexture = Texture("drone_off.png").apply {
        setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
    }

    private val droneAnimation: Animation<TextureRegion>
    private var stateTime = 0f

    init {
        val keyFrames = GdxArray<TextureRegion>()
        keyFrames.add(TextureRegion(droneOnTexture))
        for (i in 0..6) {
            keyFrames.add(TextureRegion(droneOffTexture))
        }
        droneAnimation = Animation(0.1f, keyFrames, Animation.PlayMode.LOOP)
    }

    // --- Физика ---
    private var velocityX = 0f
    private var velocityY = 0f
    private val maxSpeed = 600f
    private val acceleration = 2200f
    private val gravity = 900f
    private val drag = 0.94f
    private var rotationAngle = 0f

    // --- Монетки / Кристаллы ---
    inner class Amethyst(var x: Float, var y: Float) {
        val size = 36f
        val bounds = Rectangle(x, y, size, size)
        var collected = false

        fun update(delta: Float, speed: Float) {
            x -= speed * delta
            bounds.x = x
        }

        fun draw(batch: SpriteBatch) {
            if (!collected && amethystTexture != null) {
                batch.draw(amethystTexture, x, y, size, size)
            }
        }
    }

    private var lastBottomBlocks = -1
    // --- Препятствия (Капельники) ---
    inner class Dripstone(var x: Float) {
        val width = 64f
        val blockSize = 64f
        var scored = false

        // Генерируем высоту так, чтобы она не совпадала с предыдущим капельником
        val bottomBlocks: Int = run {
            var blocks: Int
            do {
                blocks = MathUtils.random(2, 6)
            } while (blocks == lastBottomBlocks) // Повторяем выбор, если значение совпало
            lastBottomBlocks = blocks
            blocks
        }

        val gapBlocks = 3
        val topBlocks = 12 - bottomBlocks - gapBlocks

        val bottomY = bottomBlocks * blockSize
        val topY = bottomY + gapBlocks * blockSize

        val bottomBounds = Rectangle(x, 0f, width, bottomY)
        val topBounds = Rectangle(x, topY, width, 720f - topY)

        val crystal: Amethyst? = if (MathUtils.randomBoolean(0.2f)) { // Шанс 20%
            val crystalY = bottomY + (gapBlocks * blockSize / 2f) - 18f
            Amethyst(x + (width / 2f) - 18f, crystalY)
        } else null

        fun update(delta: Float, speed: Float) {
            x -= speed * delta
            bottomBounds.x = x
            topBounds.x = x
            crystal?.update(delta, speed)
        }

        fun draw(batch: SpriteBatch) {
            for (i in 0 until bottomBlocks) {
                val drawY = i * blockSize
                val tex = when (i) {
                    0 -> bottomBaseTex
                    bottomBlocks - 1 -> bottomTipTex
                    else -> bottomMiddleTex
                }
                batch.draw(tex, x, drawY, width, blockSize)
            }

            for (i in 0 until topBlocks) {
                val drawY = topY + i * blockSize
                val tex = when (i) {
                    0 -> topTipTex
                    topBlocks - 1 -> topBaseTex
                    else -> topMiddleTex
                }
                batch.draw(tex, x, drawY, width, blockSize)
            }

            crystal?.draw(batch)
        }
    }

    private val obstacles = GdxArray<Dripstone>()
    private val spawnInterval = 1.1f // Уменьшили интервал спавна (было 1.6s)
    private var spawnTimer = 0f
    private var baseObstacleSpeed = 300f

    var score = 0
    private var isGameOver = false

    override fun show() {
        propellerSound?.play()
    }

    override fun render(delta: Float) {
        stateTime += delta

        if (!isGameOver) {
            updateLogic(delta)
        } else {
            propellerSound?.stop()
            if (Gdx.input.isKeyJustPressed(Input.Keys.R) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                restart()
            }
        }

        draw()
    }

    private fun gameOver() {
        if (!isGameOver) {
            isGameOver = true
        }
    }

    private fun updateLogic(delta: Float) {
        propellerSound?.let { sound ->
            if (!sound.isPlaying) sound.play()
        }

        velocityY -= gravity * delta

        var inputX = 0f
        var inputY = 0f

        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) inputY += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) inputY -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) inputX -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) inputX += 1f

        val controller = Controllers.getControllers().firstOrNull()
        if (controller != null) {
            val axisX = controller.getAxis(0)
            val axisY = -controller.getAxis(1)
            if (Math.abs(axisX) > 0.2f) inputX = axisX
            if (Math.abs(axisY) > 0.2f) inputY = axisY
        }

        velocityX += inputX * acceleration * delta
        velocityY += inputY * acceleration * delta

        velocityX *= drag
        velocityY *= drag
        velocityX = velocityX.coerceIn(-maxSpeed, maxSpeed)
        velocityY = velocityY.coerceIn(-maxSpeed, maxSpeed)

        droneBounds.x += velocityX * delta
        droneBounds.y += velocityY * delta
        rotationAngle = (-velocityX * 0.05f).coerceIn(-25f, 25f)

        val currentBaseSpeed = baseObstacleSpeed + (score / 10) * 20f
        val forwardBonus = if (velocityX > 0f) velocityX * 0.6f else 0f
        val actualScrollSpeed = currentBaseSpeed + forwardBonus

        bgScrollTimer += delta * (actualScrollSpeed * 0.005f)

        if (droneBounds.x < 0f) { droneBounds.x = 0f; velocityX = 0f }
        if (droneBounds.x > 1280f - droneBounds.width) { droneBounds.x = 1280f - droneBounds.width; velocityX = 0f }

        if (droneBounds.y <= 0f || droneBounds.y >= 720f - droneBounds.height) {
            gameOver()
        }

        spawnTimer += delta
        if (spawnTimer >= spawnInterval) {
            obstacles.add(Dripstone(1280f))
            spawnTimer = 0f
        }

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()

            obstacle.update(delta, actualScrollSpeed)

            if (obstacle.bottomBounds.overlaps(droneBounds) || obstacle.topBounds.overlaps(droneBounds)) {
                gameOver()
            }

            obstacle.crystal?.let { crystal ->
                if (!crystal.collected && crystal.bounds.overlaps(droneBounds)) {
                    crystal.collected = true
                    score += 5
                }
            }

            if (!obstacle.scored && obstacle.x + obstacle.width < droneBounds.x) {
                obstacle.scored = true
                score++
            }

            if (obstacle.x + obstacle.width < 0) {
                iterator.remove()
            }
        }
    }

    private fun draw() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        batch.begin()

        // --- Рисуем фон (затенение вернул) ---
        val tileSize = 64f
        val offsetX = -(bgScrollTimer * 100f) % tileSize

        batch.disableBlending()
        batch.color = Color(0.35f, 0.35f, 0.35f, 1f) // Вернули темный оттенок

        var x = offsetX - tileSize
        while (x < 1280f + tileSize) {
            var y = 0f
            while (y < 720f) {
                batch.draw(bgTexture, x, y, tileSize, tileSize)
                y += tileSize
            }
            x += tileSize
        }

        batch.enableBlending()
        batch.color = Color.WHITE

        // --- Рисуем капельники и аметисты ---
        for (obstacle in obstacles) {
            obstacle.draw(batch)
        }

        // --- Рисуем дрона ---
        if (isGameOver) {
            droneBounds.y -= gravity * 2f * Gdx.graphics.deltaTime
            rotationAngle += 150f * Gdx.graphics.deltaTime
        }

        val currentFrame = droneAnimation.getKeyFrame(stateTime)

        val drawWidth = 140f
        val drawHeight = 70f
        val drawX = droneBounds.x - (drawWidth - droneBounds.width) / 2f
        val drawY = droneBounds.y - (drawHeight - droneBounds.height) / 2f

        batch.draw(
            currentFrame,
            drawX, drawY,
            drawWidth / 2f, drawHeight / 2f,
            drawWidth, drawHeight,
            1f, 1f,
            rotationAngle
        )

        // --- Текст UI ---
        if (isGameOver) {
            font.color = Color.RED
            font.draw(batch, "GAME OVER", 530f, 420f)
            font.color = Color.WHITE
            font.draw(batch, "Press R or Space to Restart", 440f, 360f)
            font.draw(batch, "Final Score: $score", 530f, 300f)
        } else {
            font.color = Color.YELLOW
            font.draw(batch, "Score: $score", 40f, 680f)
        }

        batch.end()
    }

    private fun restart() {
        droneBounds.setPosition(200f, 360f)
        velocityX = 0f
        velocityY = 0f
        rotationAngle = 0f
        obstacles.clear()
        spawnTimer = 0f
        score = 0
        isGameOver = false
        propellerSound?.play()
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        droneOnTexture.dispose()
        droneOffTexture.dispose()
        bgTexture.dispose()
        amethystTexture?.dispose()
        topBaseTex.dispose()
        topMiddleTex.dispose()
        topTipTex.dispose()
        bottomBaseTex.dispose()
        bottomMiddleTex.dispose()
        bottomTipTex.dispose()
        propellerSound?.dispose()
    }
}