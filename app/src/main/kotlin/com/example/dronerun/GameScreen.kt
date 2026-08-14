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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Array as GdxArray

class GameScreen : ScreenAdapter() {
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
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

    private val bgTexture = Texture("stone.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val blockTex = Texture("block.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val spikeTex = Texture("spike.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }

    private val amethystTexture = try {
        Texture("amethyst_shard.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { null }

    private val batteryGoodTex = try {
        Texture("battery_green.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { null }

    private val batteryBadTex = try {
        Texture("battery_red.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { null }

    private var bgScrollTimer = 0f
    private var isPaused = false
    private var isMuted = false
    private var batterySpawnTimer = 0f
    private val droneBounds = Rectangle(200f, 360f, 100f, 50f)

    private val droneOnTexture = Texture("drone_on.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    private val droneOffTexture = Texture("drone_off.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }

    // Текстуры для анимации ходьбы
    private val walk1OnTex = try {
        Texture("walk_1_on.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { droneOnTexture }

    private val walk2OnTex = try {
        Texture("walk_2_on.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { droneOnTexture }

    private val walk1OffTex = try {
        Texture("walk_1_off.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { droneOffTexture }

    private val walk2OffTex = try {
        Texture("walk_2_off.png").apply { setFilter(TextureFilter.Nearest, TextureFilter.Nearest) }
    } catch (e: Exception) { droneOffTexture }

    private val flyAnimation: Animation<TextureRegion>
    private val walkAnimation: Animation<TextureRegion>
    private var stateTime = 0f
    private var walkStateTime = 0f

    enum class GameStage {
        SPIKES_100,
        EMPTY_TRANSITION,
        WALKING_GAME
    }

    private var currentStage = GameStage.SPIKES_100
    private var transitionDistance = 0f
    private val transitionTargetDistance = 2560f // 2 экрана (1280 * 2)

    private var batteryLevel = 100f
    private var gameOverReason = ""
    private var isBadBatteryActive = false

    private val batteries = GdxArray<Battery>()

    init {
        // Анимация полета
        val flyFrames = GdxArray<TextureRegion>()
        flyFrames.add(TextureRegion(droneOnTexture))
        flyFrames.add(TextureRegion(droneOffTexture))
        flyAnimation = Animation(0.1f, flyFrames, Animation.PlayMode.LOOP)

        // Анимация ходьбы: drone_on - walk_1_off - walk_2_on - walk_1_on - walk_2_off
        val walkFrames = GdxArray<TextureRegion>()
        walkFrames.add(TextureRegion(droneOnTexture))
        walkFrames.add(TextureRegion(walk1OffTex))
        walkFrames.add(TextureRegion(walk2OnTex))
        walkFrames.add(TextureRegion(walk1OnTex))
        walkFrames.add(TextureRegion(walk2OffTex))
        walkAnimation = Animation(0.15f, walkFrames, Animation.PlayMode.LOOP)
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

    inner class Battery(var x: Float, var y: Float, val isBad: Boolean) {
        val size = 36f
        val bounds = Rectangle(x, y, size, size)
        var collected = false

        fun update(delta: Float, speed: Float) {
            x -= speed * delta
            bounds.x = x
        }

        fun draw(batch: SpriteBatch) {
            if (!collected) {
                val tex = if (isBad) batteryBadTex else batteryGoodTex
                tex?.let { batch.draw(it, x, y, size, size) }
            }
        }
    }

    private var lastBottomBlocks = -1

    inner class ObstaclePattern(var x: Float, val isFlyingBlock: Boolean = false) {
        val width = 64f
        val blockSize = 64f
        var scored = false
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

                val bottomY = bottomBlocks * blockSize
                val crystalY = bottomY + 12f
                crystal = if (currentStage == GameStage.SPIKES_100) {
                    Amethyst(x + (width / 2f) - 18f, crystalY)
                } else null
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

                crystal = if (currentStage == GameStage.SPIKES_100 && MathUtils.randomBoolean(0.10f)) {
                    val bottomY = bottomBlocks * blockSize
                    val crystalY = bottomY + (gapBlocks * blockSize / 2f) - 18f
                    Amethyst(x + (width / 2f) - 18f, crystalY)
                } else null
            }

            val bottomY = bottomBlocks * blockSize
            val topY = 720f - (topBlocks * blockSize)
            bottomBounds = Rectangle(x, 0f, width, bottomY)
            topBounds = Rectangle(x, topY, width, topBlocks * blockSize)

            if (isDoubleGap) {
                val middleY = 3.5f * blockSize
                middleBounds = Rectangle(x, middleY, width, blockSize * 2f)
            }

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

            for (i in 0 until bottomBlocks) {
                val drawY = i * blockSize
                if (i == bottomBlocks - 1 && !isFlyingBlock) {
                    batch.draw(spikeTex, x, drawY - overlap, width, blockSize + overlap)
                } else {
                    batch.draw(blockTex, x, drawY, width, blockSize)
                }
            }

            for (i in 0 until topBlocks) {
                val drawY = 720f - ((i + 1) * blockSize)
                if (i == topBlocks - 1 && !isFlyingBlock) {
                    batch.draw(
                        spikeTex, x, drawY, width / 2f, (blockSize + overlap) / 2f,
                        width, blockSize + overlap, 1f, 1f, 180f, 0, 0,
                        spikeTex.width, spikeTex.height, false, false
                    )
                } else {
                    batch.draw(blockTex, x, drawY, width, blockSize)
                }
            }

            middleBounds?.let { m ->
                val mY = m.y
                val mH = m.height

                batch.draw(
                    spikeTex, x, mY, width / 2f, blockSize / 2f,
                    width, blockSize, 1f, 1f, 180f, 0, 0,
                    spikeTex.width, spikeTex.height, false, false
                )
                batch.draw(spikeTex, x, mY + mH - blockSize, width, blockSize)
            }

            if (sideSpikePos == 1 && bottomBlocks >= 2 && !isFlyingBlock) {
                val sideY = (bottomBlocks - 2) * blockSize
                batch.draw(
                    spikeTex, x - blockSize + 10f, sideY, blockSize / 2f, blockSize / 2f,
                    blockSize, blockSize, 1f, 1f, 90f, 0, 0,
                    spikeTex.width, spikeTex.height, false, false
                )
            } else if (sideSpikePos == 2 && topBlocks >= 2 && !isFlyingBlock) {
                val sideY = 720f - ((topBlocks - 1) * blockSize)
                batch.draw(
                    spikeTex, x - blockSize + 10f, sideY, blockSize / 2f, blockSize / 2f,
                    blockSize, blockSize, 1f, 1f, 90f, 0, 0,
                    spikeTex.width, spikeTex.height, false, false
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
        if (!isMuted) {
            propellerSound?.play()
        }
    }

    override fun render(delta: Float) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            isPaused = !isPaused
            if (isPaused) {
                propellerSound?.pause()
            } else if (!isGameOver && !isMuted) {
                propellerSound?.play()
            }
        }

        // Кнопка M для отключения звука
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            isMuted = !isMuted
            if (isMuted) {
                propellerSound?.pause()
            } else if (!isGameOver && !isPaused) {
                propellerSound?.play()
            }
        }

        stateTime += delta

        if (!isGameOver && !isPaused) {
            updateLogic(delta)
        } else if (isGameOver) {
            propellerSound?.stop()
            if (Gdx.input.isKeyJustPressed(Input.Keys.R) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                restart()
            }
        }

        draw()

        if (isPaused && !isGameOver) {
            batch.begin()
            font.color = Color.YELLOW
            font.draw(batch, "PAUSED", 570f, 400f)
            font.draw(batch, "Press ESC to Resume", 480f, 340f)
            val soundStatus = if (isMuted) "OFF" else "ON"
            font.draw(batch, "Press M to Toggle Sound [$soundStatus]", 420f, 280f)
            batch.end()
        }
    }

    private fun gameOver(reason: String = "GAME OVER") {
        if (!isGameOver) {
            isGameOver = true
            gameOverReason = reason
        }
    }

    private fun updateLogic(delta: Float) {
        // Проверка перехода на следующую стадию
        if (currentStage == GameStage.SPIKES_100 && score >= 100) {
            currentStage = GameStage.EMPTY_TRANSITION
            transitionDistance = 0f
            obstacles.clear() // Убираем все препятствия
        }

        // Расход батареи только на стадии WALKING_GAME
        if (currentStage == GameStage.WALKING_GAME) {
            val drainRate = if (isBadBatteryActive) 5.0f else 1.0f
            batteryLevel -= delta * drainRate

            if (batteryLevel <= 0f) {
                batteryLevel = 0f
                gameOver("Батарея разряжена!")
            }
        }

        propellerSound?.let { sound ->
            if (!sound.isPlaying && !isMuted && !isGameOver && !isPaused) {
                sound.play()
            }
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

        // Обновление анимации ходьбы
        val isOnGround = droneBounds.y <= 1f
        if (isOnGround) {
            walkStateTime += delta
        } else {
            walkStateTime = 0f
        }

        // Переход между стадиями
        if (currentStage == GameStage.EMPTY_TRANSITION) {
            transitionDistance += actualScrollSpeed * delta
            if (transitionDistance >= transitionTargetDistance) {
                currentStage = GameStage.WALKING_GAME
            }
        }

        if (droneBounds.x < 0f) { droneBounds.x = 0f; velocityX = 0f }
        if (droneBounds.x > 1280f - droneBounds.width) { droneBounds.x = 1280f - droneBounds.width; velocityX = 0f }
        if (droneBounds.y < 0f) droneBounds.y = 0f
        if (droneBounds.y > 720f - droneBounds.height) droneBounds.y = 720f - droneBounds.height

        // Спавн батареек только на стадии WALKING_GAME
        if (currentStage == GameStage.WALKING_GAME) {
            batterySpawnTimer += delta
            if (batterySpawnTimer >= 20f) {
                batterySpawnTimer = 0f
                val isBad = MathUtils.randomBoolean(0.10f)
                batteries.add(Battery(1280f, 360f - 18f, isBad))
            }
        }

        // Обновление батареек
        val batIterator = batteries.iterator()
        while (batIterator.hasNext()) {
            val bat = batIterator.next()
            bat.update(delta, actualScrollSpeed)

            if (!bat.collected && bat.bounds.overlaps(droneBounds)) {
                bat.collected = true
                if (bat.isBad) {
                    batteryLevel = (batteryLevel - 30f).coerceAtLeast(0f)
                    isBadBatteryActive = true
                } else {
                    batteryLevel = (batteryLevel + 20f).coerceAtMost(100f)
                    isBadBatteryActive = false
                }
            }

            if (bat.x < -50f || bat.collected) {
                batIterator.remove()
            }
        }

        // Спавн препятствий только на стадиях SPIKES_100 и WALKING_GAME
        if (currentStage == GameStage.SPIKES_100 || currentStage == GameStage.WALKING_GAME) {
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
        }

        // Обновление препятствий
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            obstacle.update(delta, actualScrollSpeed)

            val hitBottom = droneBounds.overlaps(obstacle.bottomBounds)
            val hitTop = droneBounds.overlaps(obstacle.topBounds)
            val hitMiddle = obstacle.middleBounds?.let { droneBounds.overlaps(it) } ?: false
            val hitSide = obstacle.sideSpikeBounds?.let { droneBounds.overlaps(it) } ?: false

            if (hitBottom || hitTop || hitMiddle || hitSide) {
                gameOver("Столкновение!")
                return
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

        for (bat in batteries) {
            bat.draw(batch)
        }

        if (isGameOver) {
            droneBounds.y -= gravity * 2f * Gdx.graphics.deltaTime
            rotationAngle += 150f * Gdx.graphics.deltaTime
        }

        // Выбор анимации
        val isOnGround = droneBounds.y <= 1f
        val currentFrame = if (isOnGround) {
            walkAnimation.getKeyFrame(walkStateTime)
        } else {
            flyAnimation.getKeyFrame(stateTime)
        }

        val drawWidth = 140f
        val drawHeight = 70f
        val drawX = droneBounds.x - (drawWidth - droneBounds.width) / 2f
        val drawY = droneBounds.y - (drawHeight - droneBounds.height) / 2f

        batch.draw(
            currentFrame, drawX, drawY, drawWidth / 2f, drawHeight / 2f,
            drawWidth, drawHeight, 1f, 1f, rotationAngle
        )

        if (isGameOver) {
            font.color = Color.RED
            font.draw(batch, gameOverReason, 480f, 420f)
            font.color = Color.WHITE
            font.draw(batch, "Press R or Space to Restart", 440f, 360f)
            font.draw(batch, "Final Score: $score", 530f, 300f)
        } else {
            font.color = Color.YELLOW
            font.draw(batch, "Score: $score", 40f, 680f)

            if (currentStage == GameStage.WALKING_GAME) {
                val activeTex = if (isBadBatteryActive) batteryBadTex else batteryGoodTex
                activeTex?.let { tex ->
                    batch.draw(tex, 40f, 575f, 32f, 32f)
                }
            }
        }

        batch.end()

        if (!isGameOver && currentStage == GameStage.WALKING_GAME) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

            if (isBadBatteryActive && batteryLevel <= 20f) {
                shapeRenderer.color = Color(0.35f, 0.35f, 0.35f, 0.7f)
                val smokeX = droneBounds.x - 10f + MathUtils.random(-6f, 6f)
                val smokeY = droneBounds.y + 20f + MathUtils.random(-6f, 6f)
                shapeRenderer.circle(smokeX, smokeY, MathUtils.random(10f, 18f))
            }

            shapeRenderer.color = Color.BLACK
            shapeRenderer.rect(40f, 620f, 204f, 24f)

            val barColor = when {
                batteryLevel > 60f -> Color.GREEN
                batteryLevel > 30f -> Color.ORANGE
                else -> Color.RED
            }

            shapeRenderer.color = barColor
            val fillWidth = (batteryLevel / 100f) * 200f
            shapeRenderer.rect(42f, 622f, fillWidth, 20f)
            shapeRenderer.end()

            batch.begin()
            font.color = Color.WHITE
            font.draw(batch, "${batteryLevel.toInt()}%", 255f, 642f)
            batch.end()
        }
    }

    private fun restart() {
        droneBounds.setPosition(200f, 360f)
        velocityX = 0f
        velocityY = 0f
        rotationAngle = 0f
        walkStateTime = 0f
        obstacles.clear()
        batteries.clear()
        spawnTimer = 0f
        batterySpawnTimer = 0f
        score = 0
        batteryLevel = 100f
        isBadBatteryActive = false
        isGameOver = false
        currentStage = GameStage.SPIKES_100
        transitionDistance = 0f
        if (!isMuted) {
            propellerSound?.play()
        }
    }

    override fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        droneOnTexture.dispose()
        droneOffTexture.dispose()
        walk1OnTex.dispose()
        walk2OnTex.dispose()
        walk1OffTex.dispose()
        walk2OffTex.dispose()
        bgTexture.dispose()
        amethystTexture?.dispose()
        batteryGoodTex?.dispose()
        batteryBadTex?.dispose()
        blockTex.dispose()
        spikeTex.dispose()
        propellerSound?.dispose()
    }
}