package com.mukunjin.virgube.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mukunjin.virgube.BuildConfig
import com.mukunjin.virgube.ui.theme.VirgubeColors

/** 关于界面：项目简介 + 版本号 + 致谢 + 作者信息。 */
@Composable
fun AboutScreen() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "A lightweight native Android timer for the 3x3x3 Rubik's Cube, built with Kotlin + Jetpack Compose.",
            color = VirgubeColors.OnSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))
        // 版本号与 APK 同源（根目录 gradle.properties → BuildConfig）
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            color = VirgubeColors.OnSurfaceVariant,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle("Credits")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "csTimer",
            color = VirgubeColors.OnBackground,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Scramble and timer logic referenced from and reimplemented after csTimer.",
            color = VirgubeColors.OnSurfaceVariant,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle("Author")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Virgube by mukunjin",
            color = VirgubeColors.OnBackground,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Repository: https://github.com/mukunjin/virgube",
            color = VirgubeColors.OnSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = VirgubeColors.OnBackground,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
}