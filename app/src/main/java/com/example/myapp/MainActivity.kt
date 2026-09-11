```kotlin
package com.example.myapp

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

data class Bullet(var x: Float, var y: Float, val dx: Float, val dy: Float)
data class Enemy(var x: Float, var y: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AccessibleShooterGame() } }
    }
}

@Composable
fun AccessibleShooterGame() {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var playerX by remember { mutableStateOf(480f) }
    var playerY by remember { mutableStateOf(270f) }
    var lastDx by remember { mutableStateOf(1f) }
    var lastDy by remember { mutableStateOf(0f) }
    val bullets = remember { mutableStateListOf<Bullet>() }
    val enemies = remember { mutableStateListOf<Enemy>() }
    var charging by remember { mutableStateOf(false) }
    var chargeProgress by remember { mutableStateOf(0f) }
    var announcement by remember { mutableStateOf("Game started. Use arrow keys to move. Hold space to charge, release to fire.") }
    var gameOver by remember { mutableStateOf(false) }
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    fun vibrate(ms: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(ms)
        }
    }
    LaunchedEffect(Unit) {
        repeat(5) {
            enemies.add(Enemy(Random.nextFloat() * 900f, Random.nextFloat() * 500f))
        }
    }
    LaunchedEffect(charging) {
        if (charging) {
            chargeProgress = 0f
            while (charging && chargeProgress < 1f) {
                delay(50)
                chargeProgress = (chargeProgress + 0.05f / 3f).coerceAtMost(1f)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if (gameOver) continue
            bullets.forEach { b -> b.x += b.dx; b.y += b.dy }
            bullets.removeAll { it.x < 0 || it.x > 960 || it.y < 0 || it.y > 540 }
            enemies.forEach { e ->
                if (e.x < playerX) e.x += 1.5f else if (e.x > playerX) e.x -= 1.5f
                if (e.y < playerY) e.y += 1.5f else if (e.y > playerY) e.y -= 1.5f
                if (hypot(e.x - playerX, e.y - playerY) < 30f) {
                    gameOver = true
                    announcement = "Enemy hit you. Game over."
                    vibrate(400)
                }
            }
            val hitBullets = mutableListOf<Bullet>()
            val hitEnemies = mutableListOf<Enemy>()
            for (b in bullets) for (e in enemies) {
                if (hypot(b.x - e.x, b.y - e.y) < 25f) {
                    hitBullets.add(b); hitEnemies.add(e); break
                }
            }
            bullets.removeAll(hitBullets)
            enemies.removeAll(hitEnemies)
            hitEnemies.forEach { _ ->
                enemies.add(Enemy(Random.nextFloat() * 900f, Random.nextFloat() * 500f))
                vibrate(80)
                announcement = "Enemy destroyed. ${enemies.size} remaining."
            }
        }
    }
    fun move(dx: Float, dy: Float) {
        if (gameOver) return
        val len = hypot(dx, dy)
        lastDx = dx / len; lastDy = dy / len
        playerX = (playerX + lastDx * 12f).coerceIn(20f, 940f)
        playerY = (playerY + lastDy * 12f).coerceIn(20f, 520f)
        announcement = "Position ${playerX.roundToInt()}, ${playerY.roundToInt()}"
        vibrate(20)
    }
    fun shoot() {
        if (gameOver) return
        bullets.add(Bullet(playerX, playerY, lastDx * 18f, lastDy * 18f))
        announcement = "Shot fired"
        vibrate(60)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft, Key.A -> { move(-1f, 0f); true }
                        Key.DirectionRight, Key.D -> { move(1f, 0f); true }
                        Key.DirectionUp, Key.W -> { move(0f, -1f); true }
                        Key.DirectionDown, Key.S -> { move(0f, 1f); true }
                        Key.Spacebar -> {
                            if (!charging) { charging = true; announcement = "Charging"; vibrate(100) }
                            true
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp && event.key == Key.Spacebar) {
                    if (charging) { charging = false; shoot() }
                    true
                } else false
            }
            .semantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color(0xFF14141E))
            drawRect(Color(0xFF00C8FF), topLeft = Offset(playerX - 16, playerY - 16), size = Size(32f, 32f))
            enemies.forEach { e ->
                drawRect(Color(0xFFFF3C3C), topLeft = Offset(e.x - 15, e.y - 15), size = Size(30f, 30f))
            }
            bullets.forEach { b ->
                drawRect(Color(0xFFFFFF00), topLeft = Offset(b.x - 4, b.y - 4), size = Size(8f, 8f))
            }
            if (charging) {
                drawRect(Color(0xFF505050), topLeft = Offset(20f, 500f), size = Size(300f, 20f))
                drawRect(Color(0xFF00FF00), topLeft = Offset(20f, 500f), size = Size(300f * chargeProgress, 20f))
            }
        }
        Text(
            text = if (gameOver) "GAME OVER" else if (charging) "Charging: ${(chargeProgress * 100).roundToInt()}%" else "Hold SPACE to charge",
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}
```
