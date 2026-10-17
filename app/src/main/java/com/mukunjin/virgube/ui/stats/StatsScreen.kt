package com.mukunjin.virgube.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mukunjin.virgube.data.db.SessionEntity
import com.mukunjin.virgube.data.db.SolveEntity
import com.mukunjin.virgube.scramble.CubeState
import com.mukunjin.virgube.timer.TimeFormat
import com.mukunjin.virgube.ui.scramble.CubeNet
import com.mukunjin.virgube.ui.theme.TimerFontFamily
import com.mukunjin.virgube.ui.theme.VirgubeColors
import com.mukunjin.virgube.ui.timer.TimerUiState
import com.mukunjin.virgube.ui.timer.TimerViewModel

/** 成绩窗格：分组切换 + 成绩列表（含 +2 / DNF / 删除）。 */
@Composable
fun StatsScreen(ui: TimerUiState, vm: TimerViewModel) {
    var confirmDeleteSession by remember { mutableStateOf(false) }
    /** 待确认删除的单次成绩。 */
    var confirmDeleteSolve by remember { mutableStateOf<SolveEntity?>(null) }
    /** 是否显示「新建分组」命名弹窗。 */
    var createDialog by remember { mutableStateOf(false) }
    /** 待重命名的分组。 */
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 分组切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (session in ui.sessions) {
                SessionChip(
                    name = session.name,
                    selected = session.id == ui.sessionId,
                    onClick = { vm.selectSession(session.id) },
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            SessionChip(
                name = "＋ New",
                selected = false,
                onClick = { createDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        StatsSummary(
            ui = ui,
            onRenameSession = { renameTarget = it },
            onDeleteSession = { confirmDeleteSession = true },
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 成绩列表（最新在上）
        if (ui.solves.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "No solves yet", color = VirgubeColors.Disabled, fontSize = 14.sp)
            }
        } else {
            for (solve in ui.solves.asReversed()) {
                SolveRow(
                    index = ui.solves.indexOf(solve) + 1,
                    solve = solve,
                    useMilli = ui.useMilli,
                    onTogglePlus2 = {
                        vm.setPenalty(solve, if (solve.penalty == 2000) 0 else 2000)
                    },
                    onToggleDnf = {
                        vm.setPenalty(solve, if (solve.penalty == -1) 0 else -1)
                    },
                    onDelete = { confirmDeleteSolve = solve },
                    onClick = { vm.openSolveDetail(solve) },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (confirmDeleteSession) {
        val session = ui.sessions.firstOrNull { it.id == ui.sessionId }
        AlertDialog(
            onDismissRequest = { confirmDeleteSession = false },
            title = { Text("Delete group") },
            text = { Text("Delete \"${session?.name ?: ""}\" and all its solves? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSession = false
                    session?.let { vm.deleteSession(it) }
                }) { Text("Delete", color = VirgubeColors.TimerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSession = false }) { Text("Cancel") }
            },
        )
    }

    confirmDeleteSolve?.let { solve ->
        AlertDialog(
            onDismissRequest = { confirmDeleteSolve = null },
            title = { Text("Delete solve") },
            text = {
                Text(
                    "Delete this solve (" +
                        TimeFormat.prettyPenalty(solve.totalMs, solve.penalty, ui.useMilli) +
                        ")? This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSolve = null
                    vm.deleteSolve(solve)
                }) { Text("Delete", color = VirgubeColors.TimerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSolve = null }) { Text("Cancel") }
            },
        )
    }

    if (createDialog) {
        SessionNameDialog(
            title = "New group",
            initial = vm.suggestGroupName(),
            existingNames = ui.sessions.map { it.name },
            originalName = null,
            onConfirm = {
                createDialog = false
                vm.addSession(it)
            },
            onDismiss = { createDialog = false },
        )
    }

    renameTarget?.let { target ->
        SessionNameDialog(
            title = "Rename group",
            initial = target.name,
            existingNames = ui.sessions.map { it.name },
            originalName = target.name,
            onConfirm = {
                renameTarget = null
                vm.renameSession(target.id, it)
            },
            onDismiss = { renameTarget = null },
        )
    }
}

/** 分组名规则：仅中英文（可含数字与空格），最长 20 字；列表展示最多 10 字。 */
private const val NAME_MAX = 20
private const val NAME_DISPLAY_MAX = 10
private val NAME_ALLOWED = Regex("[\\u4e00-\\u9fa5A-Za-z0-9 ]+")

/** 分组名的列表展示文本：超过 [NAME_DISPLAY_MAX] 字时截断并加省略号。 */
private fun displayName(name: String): String =
    if (name.length <= NAME_DISPLAY_MAX) name else name.take(NAME_DISPLAY_MAX) + "…"

/**
 * 分组命名弹窗（新建 / 重命名共用）：
 * 仅允许中英文（可含数字与空格）、最长 [NAME_MAX] 字、且不能与其它分组重名。
 */
@Composable
private fun SessionNameDialog(
    title: String,
    initial: String,
    existingNames: List<String>,
    originalName: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val trimmed = text.trim()
    val error = when {
        trimmed.isEmpty() -> "Name cannot be empty"
        trimmed.length > NAME_MAX -> "At most $NAME_MAX characters"
        !NAME_ALLOWED.matches(trimmed) -> "Only letters, digits and spaces allowed"
        existingNames.any { it != originalName && it.equals(trimmed, ignoreCase = true) } -> "Name already exists"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= NAME_MAX) text = it },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = error.orEmpty(), color = VirgubeColors.TimerRed, fontSize = 12.sp)
                    Text(
                        text = "${text.length}/$NAME_MAX",
                        color = VirgubeColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = { onConfirm(trimmed) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** 会话汇总：顶部当前 ao5/ao12 预览 + 最佳/最差两栏切换 + 其它统计。 */
@Composable
private fun StatsSummary(
    ui: TimerUiState,
    onRenameSession: (SessionEntity) -> Unit,
    onDeleteSession: () -> Unit,
) {
    val s = ui.stats
    val milli = ui.useMilli
    /** 成绩位列切换：true 显示最佳，false 显示最差。 */
    var showBest by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部：当前 ao5 / ao12 预览
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("ao5", TimeFormat.prettyAvgOrDnf(s.ao5, milli), Modifier.weight(1f))
            StatCard("ao12", TimeFormat.prettyAvgOrDnf(s.ao12, milli), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 最佳 / 最差两栏切换，各含 单次 / ao5 / ao12 / ao100
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VirgubeColors.SurfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            RankTabs(showBest = showBest, onSelect = { showBest = it })
            Spacer(modifier = Modifier.height(6.dp))
            val single = if (showBest) s.bestSingleMs else s.worstSingleMs
            StatRow("Single", single?.let { TimeFormat.pretty(it, milli) } ?: "-")
            StatRow("ao5", avgText(if (showBest) s.bestAo5 else s.worstAo5, milli))
            StatRow("ao12", avgText(if (showBest) s.bestAo12 else s.worstAo12, milli))
            StatRow("ao100", avgText(if (showBest) s.bestAo100 else s.worstAo100, milli))
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 其它统计
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VirgubeColors.SurfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            StatRow("Mean", avgText(s.meanMs, milli))
            StatRow("+2", "${s.plus2Count}")
            StatRow("DNF", "${s.dnfCount}")
            StatRow("Count", "${s.count}")
            Spacer(modifier = Modifier.height(10.dp))
            val current = ui.sessions.firstOrNull { it.id == ui.sessionId }
            // 「重命名」「删除分组」紧凑胶囊，全部靠右下方对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                SessionActionCapsule(
                    label = "Rename",
                    danger = false,
                    onClick = { current?.let(onRenameSession) },
                )
                SessionActionCapsule(
                    label = "Delete group",
                    danger = true,
                    onClick = onDeleteSession,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/** 平均成绩文本：数据不足或为 DNF 时显示 "-"。 */
private fun avgText(value: Double?, milli: Boolean): String =
    if (value == null) "-" else TimeFormat.prettyRounded(value, milli)

/** 最佳 / 最差两栏切换按钮。 */
@Composable
private fun RankTabs(showBest: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RankTab("Best", selected = showBest, onClick = { onSelect(true) }, modifier = Modifier.weight(1f))
        RankTab("Worst", selected = !showBest, onClick = { onSelect(false) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RankTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) VirgubeColors.ButtonFill else VirgubeColors.Surface)
            .border(1.dp, VirgubeColors.Border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) VirgubeColors.OnButton else VirgubeColors.OnSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VirgubeColors.SurfaceVariant)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label.uppercase(), color = VirgubeColors.OnSurfaceVariant, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = VirgubeColors.OnBackground,
            fontSize = 26.sp,
            fontFamily = TimerFontFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = VirgubeColors.OnSurfaceVariant, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = VirgubeColors.OnBackground,
            fontSize = 16.sp,
            fontFamily = TimerFontFamily,
        )
    }
}

@Composable
private fun SessionChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) VirgubeColors.ButtonFill else VirgubeColors.Surface)
            .border(1.dp, VirgubeColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = displayName(name),
            color = if (selected) VirgubeColors.OnButton else VirgubeColors.OnSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SolveRow(
    index: Int,
    solve: SolveEntity,
    useMilli: Boolean,
    onTogglePlus2: () -> Unit,
    onToggleDnf: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val timeText = TimeFormat.prettyPenalty(solve.totalMs, solve.penalty, useMilli)
    val timeColor = when (solve.penalty) {
        -1 -> VirgubeColors.TimerRed
        2000 -> VirgubeColors.TimerYellow
        else -> VirgubeColors.OnBackground
    }
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VirgubeColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$index",
            color = VirgubeColors.Disabled,
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = timeText,
            color = timeColor,
            fontSize = 18.sp,
            fontFamily = TimerFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        ActionText("+2", solve.penalty == 2000, onTogglePlus2)
        ActionText("DNF", solve.penalty == -1, onToggleDnf)
        ActionText("×", active = false, onClick = onDelete, danger = true)
    }
}

/** 药丸形操作按钮；[danger] 为真时使用红色底以强调危险操作。 */
@Composable
private fun ActionText(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    val fill = when {
        danger -> VirgubeColors.TimerRed
        active -> VirgubeColors.ButtonFill
        else -> VirgubeColors.Surface
    }
    val content = when {
        danger -> Color.White
        active -> VirgubeColors.OnButton
        else -> VirgubeColors.OnSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 34.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (danger) VirgubeColors.TimerRed else VirgubeColors.Border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = content, fontSize = 14.sp)
    }
}

/** 分组操作胶囊（重命名 / 删除），宽度随文字紧凑自适应；高度小于底部胶囊栏。 */
@Composable
private fun SessionActionCapsule(
    label: String,
    danger: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(if (danger) VirgubeColors.TimerRed else VirgubeColors.ButtonFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (danger) Color.White else VirgubeColors.OnButton,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/**
 * 成绩详情浮层（应用内叠加层，非独立弹窗窗口）：
 * 同时展示该次成绩与其对应打乱、打乱展开图。点击遮罩或「关闭」收起。
 */
@Composable
fun SolveDetailOverlay(solve: SolveEntity, useMilli: Boolean, onClose: () -> Unit) {
    val scramble = solve.scramble
    val timeColor = when (solve.penalty) {
        -1 -> VirgubeColors.TimerRed
        2000 -> VirgubeColors.TimerYellow
        else -> VirgubeColors.OnBackground
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(VirgubeColors.SurfaceVariant)
                // 吞掉卡片内部点击，避免误触遮罩关闭
                .clickable(onClick = {})
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Solve", color = VirgubeColors.OnSurfaceVariant, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = TimeFormat.prettyPenalty(solve.totalMs, solve.penalty, useMilli),
                color = timeColor,
                fontSize = 30.sp,
                fontFamily = TimerFontFamily,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Scramble", color = VirgubeColors.OnSurfaceVariant, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = scramble.ifEmpty { "(No scramble recorded)" },
                color = VirgubeColors.OnBackground,
                fontSize = 15.sp,
                fontFamily = TimerFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (scramble.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                CubeNet(
                    facelets = remember(solve.id) { CubeState.faceletsOf(scramble) },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}
