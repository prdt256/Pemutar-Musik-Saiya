package com.example.pemutarmusik.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pemutarmusik.data.SortBy
import com.example.pemutarmusik.data.SortOrder

/**
 * Header: pilihan sortir lengkap + jumlah lagu + tombol Putar Semua & Acak.
 */
@Composable
fun SortHeader(
    total: Int,
    sortBy: SortBy,
    sortOrder: SortOrder,
    onChangeSortBy: (SortBy) -> Unit,
    onToggleOrder: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    shuffleActive: Boolean = false
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Baris atas: Sortir dropdown + toggle ASC/DESC + jumlah lagu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Tombol pilih kriteria sortir (dropdown) ──
            Box {
                TextButton(
                    onClick = { showSortMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = when (sortBy) {
                            SortBy.TITLE      -> Icons.Default.SortByAlpha
                            SortBy.ARTIST     -> Icons.Default.Person
                            SortBy.DURATION   -> Icons.Default.Timer
                            SortBy.DATE_ADDED -> Icons.Default.CalendarToday
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (sortBy) {
                            SortBy.TITLE      -> "Judul"
                            SortBy.ARTIST     -> "Artis"
                            SortBy.DURATION   -> "Durasi"
                            SortBy.DATE_ADDED -> "Terbaru"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    listOf(
                        SortBy.TITLE      to Pair(Icons.Default.SortByAlpha,   "Judul"),
                        SortBy.ARTIST     to Pair(Icons.Default.Person,         "Artis"),
                        SortBy.DURATION   to Pair(Icons.Default.Timer,          "Durasi"),
                        SortBy.DATE_ADDED to Pair(Icons.Default.CalendarToday,  "Terbaru Ditambahkan")
                    ).forEach { (sort, info) ->
                        val (icon, label) = info
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(icon, null,
                                    tint = if (sort == sortBy) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp))
                            },
                            text = {
                                Text(
                                    label,
                                    color = if (sort == sortBy) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (sort == sortBy) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onChangeSortBy(sort)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // ── Tombol toggle ASC / DESC ──
            IconButton(
                onClick = onToggleOrder,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (sortOrder == SortOrder.ASC)
                        Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (sortOrder == SortOrder.ASC) "A-Z" else "Z-A",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "$total lagu",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        // Baris bawah: Putar Semua + Acak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Putar semua", fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (shuffleActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else androidx.compose.ui.graphics.Color.Transparent
                ),
                border = androidx.compose.foundation.BorderStroke(
                    if (shuffleActive) 2.dp else 1.dp, MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Acak", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
