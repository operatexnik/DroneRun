package com.example.dronerun

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.audio.Music
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

    private val propellerSound: Music? = try {
        if (Gdx.files.internal("propeller.mp3").exists()) {
            Gdx.audio.newMusic(Gdx.files.internal("propeller.mp3")).apply {
                isLooping = true
                volume = 0.5f
            }
        } else null
    } catch (e: Exception) { null }

    private val bgTexture = Texture("stone.png").apply {
        setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
    }

    private val amethystTexture = try {
        Texture("amethyst_shard.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { null }

    private val blockTex = Texture("block.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val spikeTex = Texture("spike.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }

    private var bgScrollTimer = 0f

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

    private var velocityX = 0f
    private var velocityY = 0f
    private val maxSpeed = 600f
    private val accelerationY = 2200f
    private val accelerationX = 1500f
    private val gravity = 900f
    private val drag = 0.94f
    private var rotationAngle = 0f

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

    inner class ObstaclePattern(var x: Float, val isFlyingBlock: Boolean = false) {
        val width = 64f
        val blockSize = 64f
        var scored = false

        // Понизили шанс спавна "Двойного пролёта" до 15% (было 25%)
        val isDoubleGap: Boolean = !isFlyingBlock && MathUtils.randomBoolean(0.15f)

        val bottomBlocks: Int
        val topBlocks: Int
        val gapBlocks: Int

        var sideSpikePos: Int = 0

        val bottomBounds: Rectangle
        val topBounds: Rectangle
        var middleBounds: Rectangle? = null
        var sideSpikeBounds: Rectangle? = null
        val crystal: Amethyst?

        init {
            if (isFlyingBlock) {
                sideSpikePos = 0
                val passTop = MathUtils.randomBoolean()
                if (passTop) {
                    bottomBlocks = 7
                    topBlocks = 0
                } else {
                    bottomBlocks = 0
                    topBlocks = 7
                }
                gapBlocks = 4
                crystal = null
            } else if (isDoubleGap) {
                sideSpikePos = 0
                bottomBlocks = 2
                topBlocks = 2
                gapBlocks = 7

                // Шард теперь спавнится ВНИЗУ — в самом узком месте между нижним шипом и средним блоком!
                val bottomY = bottomBlocks * blockSize // Высота нижнего столба с шипом (128f)
                val crystalY = bottomY + 12f // Зажимаем кристалл сразу над нижним шипом
                crystal = Amethyst(x + (width / 2f) - 18f, crystalY)
            } else {
                if (MathUtils.randomBoolean(0.30f)) {
                    sideSpikePos = MathUtils.random(1, 2)
                } else {
                    sideSpikePos = 0
                }

                var blocks: Int
                do {
                    blocks = MathUtils.random(1, 4)
                } while (blocks == lastBottomBlocks)
                lastBottomBlocks = blocks

                bottomBlocks = blocks

                val hasSideSpike = (sideSpikePos == 1 && bottomBlocks >= 2) || (sideSpikePos == 2 && (11 - bottomBlocks - 4) >= 2)
                gapBlocks = if (hasSideSpike) 5 else 4
                topBlocks = (11 - bottomBlocks - gapBlocks).coerceAtLeast(0)

                crystal = if (MathUtils.randomBoolean(0.10f)) {
                    val bottomY = bottomBlocks * blockSize
                    val crystalY = bottomY + (gapBlocks * blockSize / 2f) - 18f
                    Amethyst(x + (width / 2f) - 18f, crystalY)
                } else null
            }

            // Гарантированная инициализация прямоугольников
            val bottomY = bottomBlocks * blockSize
            val topY = 720f - (topBlocks * blockSize)
            bottomBounds = Rectangle(x, 0f, width, bottomY)
            topBounds = Rectangle(x, topY, width, topBlocks * blockSize)

            // Средний блок для Двойного пролёта
            if (isDoubleGap) {
                val middleY = 3.5f * blockSize
                middleBounds = Rectangle(x, middleY, width, blockSize * 2f)
            }

            // Боковые шипы
            sideSpikeBounds = when {
                sideSpikePos == 1 && bottomBlocks >= 2 && !isFlyingBlock -> {
                    val sideY = (bottomBlocks - 2) * blockSize
                    Rectangle(x - 24f, sideY + 12f, 24f, blockSize - 24f)
                }
                sideSpikePos == 2 && topBlocks >= 2 && !isFlyingBlock -> {
                    val sideY = 720f - ((topBlocks - 1) * blockSize)
                    Rectangle(x - 24f, sideY + 12f, 24f, blockSize - 24f)
                }
                else -> null
            }
        }

        fun update(delta: Float, speed: Float) {
            x -= speed * delta
            bottomBounds.x = x
            topBounds.x = x
            middleBounds?.x = x
            sideSpikeBounds?.x = x - 24f
            crystal?.update(delta, speed)
        }

        fun draw(batch: SpriteBatch) {
            val overlap = 2f

            // 1. Нижний столб
            for (i in 0 until bottomBlocks) {
                val drawY = i * blockSize
                if (i == bottomBlocks - 1 && !isFlyingBlock) {
                    batch.draw(spikeTex, x, drawY - overlap, width, blockSize + overlap)
                } else {
                    batch.draw(blockTex, x, drawY, width, blockSize)
                }
            }

            // 2. Верхний столб
            for (i in 0 until topBlocks) {
                val drawY = 720f - ((i + 1) * blockSize)
                if (i == topBlocks - 1 && !isFlyingBlock) {
                    batch.draw(
                        spikeTex,
                        x, drawY,
                        width / 2f, (blockSize + overlap) / 2f,
                        width, blockSize + overlap,
                        1f, 1f,
                        180f, 0, 0,
                        spikeTex.width, spikeTex.height,
                        false, false
                    )
                } else {
                    batch.draw(blockTex, x, drawY, width, blockSize)
                }
            }

            // 3. Средний блок с двухсторонними шипами
            middleBounds?.let { m ->
                val mY = m.y
                val mH = m.height

                batch.draw(
                    spikeTex,
                    x, mY,
                    width / 2f, blockSize / 2f,
                    width, blockSize,
                    1f, 1f,
                    180f, 0, 0,
                    spikeTex.width, spikeTex.height,
                    false, false
                )

                batch.draw(spikeTex, x, mY + mH - blockSize, width, blockSize)
            }

            // 4. Отрисовка бокового шипа
            if (sideSpikePos == 1 && bottomBlocks >= 2 && !isFlyingBlock) {
                val sideY = (bottomBlocks - 2) * blockSize
                batch.draw(
                    spikeTex,
                    x - blockSize + 10f, sideY,
                    blockSize / 2f, blockSize / 2f,
                    blockSize, blockSize,
                    1f, 1f,
                    90f, 0, 0,
                    spikeTex.width, spikeTex.height,
                    false, false
                )
            } else if (sideSpikePos == 2 && topBlocks >= 2 && !isFlyingBlock) {
                val sideY = 720f - ((topBlocks - 1) * blockSize)
                batch.draw(
                    spikeTex,
                    x - blockSize + 10f, sideY,
                    blockSize / 2f, blockSize / 2f,
                    blockSize, blockSize,
                    1f, 1f,
                    90f, 0, 0,
                    spikeTex.width, spikeTex.height,
                    false, false
                )
            }

            crystal?.draw(batch)
        }
    }

    private val obstacles = GdxArray<ObstaclePattern>()
    private val spawnInterval = 1.15f
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

        velocityX += inputX * accelerationX * delta
        velocityY += inputY * accelerationY * delta

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

        // МЯГКИЕ ГРАНИЦЫ ЭКРАНА (без смерти)
        if (droneBounds.x < 0f) { droneBounds.x = 0f; velocityX = 0f }
        if (droneBounds.x > 1280f - droneBounds.width) { droneBounds.x = 1280f - droneBounds.width; velocityX = 0f }
        if (droneBounds.y < 0f) droneBounds.y = 0f
        if (droneBounds.y > 720f - droneBounds.height) droneBounds.y = 720f - droneBounds.height

        // --- NOCLIP: Смерть от границ экрана отключена ---
        if (droneBounds.y <= 0f || droneBounds.y >= 720f - droneBounds.height) { gameOver() }

        spawnTimer += delta
        if (spawnTimer >= spawnInterval) {
            val is15Level = (score > 0) && (score % 15 == 0)

            obstacles.add(ObstaclePattern(1280f, isFlyingBlock = is15Level))

            if (!is15Level && MathUtils.randomBoolean(0.20f)) {
                obstacles.add(ObstaclePattern(1280f + 320f))
                spawnTimer = -0.4f
            } else {
                spawnTimer = 0f
            }
        }

        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()

            obstacle.update(delta, actualScrollSpeed)

            // --- NOCLIP: Проверка столкновений отключена ---

            val hitsBottom = obstacle.bottomBounds.overlaps(droneBounds)
            val hitsTop = obstacle.topBounds.overlaps(droneBounds)
            val hitsSideSpike = obstacle.sideSpikeBounds?.overlaps(droneBounds) == true

            if (hitsBottom || hitsTop || hitsSideSpike) {
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

        val tileSize = 64f
        val offsetX = -(bgScrollTimer * 100f) % tileSize

        batch.disableBlending()
        batch.color = Color(0.12f, 0.12f, 0.14f, 1f)

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

        for (obstacle in obstacles) {
            obstacle.draw(batch)
        }

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
        blockTex.dispose()
        spikeTex.dispose()
        propellerSound?.dispose()
    }
}