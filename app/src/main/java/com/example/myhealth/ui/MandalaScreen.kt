package com.example.myhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myhealth.ai.OpenAiClient
import com.example.myhealth.session.DailySessionViewModel
import com.example.myhealth.session.LocalSessionRepository
import kotlinx.coroutines.launch

@Composable
fun MandalaScreen(vm: DailySessionViewModel, onDone: () -> Unit) {
    val repo = remember { LocalSessionRepository() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val palette = listOf(
        Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF43A047),
        Color(0xFF039BE5), Color(0xFF5E35B1), Color(0xFFD81B60), Color(0xFF8D6E63)
    )
    val cells = remember { mutableStateListOf<Color?>().apply { repeat(36) { add(null) } } }
    var selected by remember { mutableStateOf(palette.first()) }
    var saving by remember { mutableStateOf(false) }

    fun saveAndNext() {
        scope.launch {
            saving = true
            val fillRatio = cells.count { it != null } / cells.size.toFloat()
            val warmRatio = cells.count { it == palette[0] || it == palette[1] || it == palette[2] } / cells.size.toFloat()
            val score = (50 + fillRatio * 45).toInt().coerceIn(0, 100)
            val analysis = OpenAiClient.analyzeColoringReturnJson("")
            vm.setMandala(symmetry = 0.75f, fillRatio = fillRatio, warmRatio = warmRatio)
            repo.insertColoring(
                templateId = "grid-mandala",
                score = score,
                analysisJson = analysis,
                imageBase64 = null
            )
            saving = false
            onDone()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("만다라 색칠", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        itemsIndexed(cells) { index, color ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .aspectRatio(1f)
                                    .background(color ?: MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                    .clickable { cells[index] = selected }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color, MaterialTheme.shapes.small)
                            .border(
                                width = if (selected == color) 3.dp else 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable { selected = color }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { cells.indices.forEach { cells[it] = null } },
                    modifier = Modifier.weight(1f),
                    enabled = !saving
                ) {
                    Text("초기화")
                }
                Button(
                    onClick = {
                        if (cells.none { it != null }) {
                            scope.launch { snack.showSnackbar("한 칸 이상 색칠해 주세요.") }
                        } else {
                            saveAndNext()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !saving
                ) {
                    Text(if (saving) "저장 중..." else "저장하고 요약")
                }
            }
        }
    }
}
