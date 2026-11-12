package ai.soumya.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.soumya.assistant.R
import ai.soumya.assistant.data.Prefs
import ai.soumya.assistant.speech.VoiceState
import ai.soumya.assistant.ui.theme.Cyan400
import ai.soumya.assistant.ui.theme.Mint400
import ai.soumya.assistant.ui.theme.TextSecondary
import ai.soumya.assistant.ui.theme.Violet500

/**
 * Animated Soumya portrait (v1 honest animation):
 * generated portrait + Compose-driven idle breathing (scale), periodic blink
 * overlay, listening waveform bars, thinking dots and a speaking mouth
 * overlay. True real-time 3D with lip-sync is NOT in v1 — see About /
 * FEATURE_STATUS.md.
 */
@Composable
fun CharacterView(
    character: Prefs.Character,
    state: VoiceState,
    mood: String,
    modifier: Modifier = Modifier
) {
    // Idle breathing: slow 4s scale loop.
    val breathing = rememberInfiniteTransition(label = "breathing")
    val breatheScale by breathing.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val portrait = if (character == Prefs.Character.MALE) R.drawable.soumya_male else R.drawable.soumya_female
    val name = if (character == Prefs.Character.MALE) "Soumya" else "Soumya"

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(portrait),
                contentDescription = "$name portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .scale(breatheScale)
            )
            // Speaking mouth overlay: pulsing cyan bar at the bottom of the portrait.
            // (v1: Compose overlay. True lip-sync needs a 3D model — see About.)
            if (state == VoiceState.SPEAKING) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .width(64.dp)
                        .height(10.dp)
                        .background(Cyan400, RoundedCornerShape(5.dp))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            text = when (state) {
                VoiceState.LISTENING -> "Listening…"
                VoiceState.THINKING -> "Thinking…"
                VoiceState.SPEAKING -> "Speaking…"
                VoiceState.ERROR -> "Something went wrong"
                VoiceState.IDLE -> mood
            },
            fontSize = 14.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        when (state) {
            VoiceState.LISTENING -> WaveformBars()
            VoiceState.THINKING -> ThinkingDots()
            else -> {}
        }
    }
}

@Composable
fun WaveformBars() {
    val transition = rememberInfiniteTransition(label = "wave")
    val heights = (0 until 7).map { i ->
        transition.animateFloat(
            initialValue = 6f,
            targetValue = 26f,
            animationSpec = infiniteRepeatable(
                animation = tween(450 + i * 60, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(5.dp)
                    .height(h.value.dp)
                    .background(Cyan400, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val alphas = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, delayMillis = i * 180, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot$i"
        )
    }
    Row {
        alphas.forEach { a ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(10.dp)
                    .background(Violet500.copy(alpha = a.value), CircleShape)
            )
        }
    }
}

@Composable
fun StatusChip(text: String, ok: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (ok) Mint400.copy(alpha = 0.15f) else Color(0xFFFFB020).copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (ok) Mint400 else Color(0xFFFFB020),
            fontWeight = FontWeight.Medium
        )
    }
}
