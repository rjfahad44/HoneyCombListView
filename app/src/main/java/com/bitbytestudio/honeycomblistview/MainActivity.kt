package com.bitbytestudio.honeycomblistview

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.bitbytestudio.honeycomblistview.honeycomb.FlatHexagonShape
import com.bitbytestudio.honeycomblistview.honeycomb.Honeycomb2DGrid
import com.bitbytestudio.honeycomblistview.honeycomb.Honeycomb2DPagingGrid
import com.bitbytestudio.honeycomblistview.honeycomb.PointyHexagonShape
import com.bitbytestudio.honeycomblistview.ui.theme.HoneyCombListViewTheme

data class ArtistItem(
    val id: String,
    val name: String,
    val genre: String,
    val imageUrl: String
)

enum class HoneycombShapeType {
    CIRCLE, POINTY_HEXAGON, FLAT_HEXAGON
}

class SampleArtistPagingSource : PagingSource<Int, ArtistItem>() {
    override fun getRefreshKey(state: PagingState<Int, ArtistItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ArtistItem> {
        val page = params.key ?: 1
        val pageSize = 20
        val startId = (page - 1) * pageSize

        val genres = listOf("Pop", "Rock", "Hip-Hop", "Jazz", "Electronic", "R&B", "Indie", "Classical")
        val names = listOf(
            "Aura Nova", "Blaze Beats", "Celestial Sound", "Drift Horizon", "Echo Pulse",
            "Frequency Flow", "Groove Theory", "Harmonic Wave", "Infinite Synth", "Jazzy Vibe",
            "Krypton Bass", "Lunar Melody", "Mystic Chord", "Neon Rhythm", "Opal Audio"
        )

        val items = List(pageSize) { index ->
            val id = startId + index + 1
            ArtistItem(
                id = "paging_artist_$id",
                name = "${names[(id - 1) % names.size]} #$id",
                genre = genres[id % genres.size],
                imageUrl = "https://picsum.photos/seed/paging_$id/300/300"
            )
        }

        return LoadResult.Page(
            data = items,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (page >= 10) null else page + 1
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sampleList = List(50) { index ->
            val id = index + 1
            val genres = listOf("Pop", "Rock", "Hip-Hop", "Jazz", "Electronic", "R&B")
            val artistNames = listOf(
                "Taylor Swift", "The Weeknd", "Drake", "Billie Eilish", "Ed Sheeran",
                "Dua Lipa", "Post Malone", "Ariana Grande", "Kendrick Lamar", "Eminem",
                "Coldplay", "Imagine Dragons", "Bruno Mars", "Rihanna", "Beyoncé",
                "Justin Bieber", "Katy Perry", "SZA", "Bad Bunny", "BTS"
            )
            ArtistItem(
                id = "list_artist_$id",
                name = artistNames[(id - 1) % artistNames.size],
                genre = genres[id % genres.size],
                imageUrl = "https://picsum.photos/seed/$id/300/300"
            )
        }

        val pagingFlow = Pager(PagingConfig(pageSize = 20)) {
            SampleArtistPagingSource()
        }.flow

        setContent {
            HoneyCombListViewTheme {
                HoneycombDemoApp(sampleList = sampleList, pagingFlow = pagingFlow)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoneycombDemoApp(
    sampleList: List<ArtistItem>,
    pagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<ArtistItem>>
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var gridColumns by remember { mutableIntStateOf(0) }
    var shapeType by remember { mutableStateOf(HoneycombShapeType.POINTY_HEXAGON) }

    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()

    val itemShape: Shape = when (shapeType) {
        HoneycombShapeType.CIRCLE -> CircleShape
        HoneycombShapeType.POINTY_HEXAGON -> PointyHexagonShape
        HoneycombShapeType.FLAT_HEXAGON -> FlatHexagonShape
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Honeycomb 2D Compose",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Tabs (2D Free Drag Grid vs 2D Paging Grid vs 1D Row List)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("2D Honeycomb Grid") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("2D Paging Grid") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Configuration Controls
            ControlsCard(
                shapeType = shapeType,
                onShapeTypeChange = { shapeType = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (selectedTab) {
                    0 -> "Drag in ANY direction (up, down, left, right, diagonal)"
                    else -> "2D Paginated Drag Grid"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> {
                        Honeycomb2DGrid(
                            items = sampleList,
                            columns = gridColumns,
                            itemSize = 110.dp,
                            spacingX = 6.dp,
                            spacingY = 6.dp,
                            maxScale = 1.20f,
                            minScale = 0.40f,
                            maxAlpha = 1.0f,
                            minAlpha = 0.20f
                        ) { artist, _, modifier ->
                            ArtistHoneycombCell(
                                artist = artist,
                                shape = itemShape,
                                modifier = modifier,
                                onClick = {
                                    Toast.makeText(context, "Clicked: ${it.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    else -> {
                        Honeycomb2DPagingGrid(
                            items = lazyPagingItems,
                            columns = gridColumns,
                            itemSize = 110.dp,
                            spacingX = 6.dp,
                            spacingY = 6.dp,
                            maxScale = 1.20f,
                            minScale = 0.40f,
                            maxAlpha = 1.0f,
                            minAlpha = 0.20f
                        ) { artist, _, modifier ->
                            ArtistHoneycombCell(
                                artist = artist,
                                shape = itemShape,
                                modifier = modifier,
                                onClick = {
                                    Toast.makeText(context, "Clicked: ${it.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlsCard(
    shapeType: HoneycombShapeType,
    onShapeTypeChange: (HoneycombShapeType) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Shape:", style = MaterialTheme.typography.bodySmall)
                FilterChip(
                    selected = shapeType == HoneycombShapeType.POINTY_HEXAGON,
                    onClick = { onShapeTypeChange(HoneycombShapeType.POINTY_HEXAGON) },
                    label = { Text("Pointy Hex") }
                )
                FilterChip(
                    selected = shapeType == HoneycombShapeType.FLAT_HEXAGON,
                    onClick = { onShapeTypeChange(HoneycombShapeType.FLAT_HEXAGON) },
                    label = { Text("Flat Hex") }
                )
                FilterChip(
                    selected = shapeType == HoneycombShapeType.CIRCLE,
                    onClick = { onShapeTypeChange(HoneycombShapeType.CIRCLE) },
                    label = { Text("Circle") }
                )
            }
        }
    }
}

@Composable
fun ArtistHoneycombCell(
    artist: ArtistItem,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (ArtistItem) -> Unit
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable { onClick(artist) }
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = shape
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist.genre,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
