# HoneyCombListView 🐝

A high-performance 2D Honeycomb / Hexagonal Grid list component for **Jetpack Compose**. Featuring smooth fisheye scaling, depth effects, gesture panning with fling momentum, and native support for Jetpack Paging 3.

---

## 📹 Preview

<p align="center">
  <img src="demo/honeyCombListView.gif" alt="HoneyCombListView Demo" width="320" />
</p>

---

## ✨ Features

- 🐝 **Honeycomb Layout**: Circular & multi-column hexagonal grid placement algorithm.
- 🔍 **Fisheye Depth & Focus Effect**: Dynamic center-focused scaling (`maxScale` to `minScale`) and alpha fading (`maxAlpha` to `minAlpha`).
- 🖐️ **Smooth Panning & Fling Inertia**: Physics-based drag tracking and fling velocity decay with boundary clamping.
- ⚡ **Performance Optimized**: Zero recomposition overhead during pan gestures using Compose `graphicsLayer` transformations.
- 📜 **Jetpack Paging 3 Ready**: Dedicated `Honeycomb2DPagingGrid` for seamless infinite scrolling with `LazyPagingItems`.
- 🔷 **Custom Hexagonal Shapes**: Pre-packaged `PointyHexagonShape` and `FlatHexagonShape`.

---

## 📦 Installation

### 1. Add JitPack Repository

Add JitPack to your `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency

Add `HoneyCombListView` to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.rjfahad44:HoneyCombListView:1.0.0")
}
```

---

## 🚀 Quick Start

### Standard List (`Honeycomb2DGrid`)

```kotlin
import com.bitbytestudio.honeycomblistview.honeycomb.Honeycomb2DGrid
import com.bitbytestudio.honeycomblistview.honeycomb.PointyHexagonShape

@Composable
fun HoneycombSampleScreen(items: List<ArtistItem>) {
    Honeycomb2DGrid(
        items = items,
        columns = 0, // 0 for auto-calculation
        itemSize = 96.dp,
        spacingX = 8.dp,
        spacingY = 8.dp,
        maxScale = 1.15f,
        minScale = 0.45f
    ) { item, index, modifier ->
        Box(
            modifier = modifier
                .clip(PointyHexagonShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = item.name,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
```

### Jetpack Paging 3 (`Honeycomb2DPagingGrid`)

```kotlin
import androidx.paging.compose.collectAsLazyPagingItems
import com.bitbytestudio.honeycomblistview.honeycomb.Honeycomb2DPagingGrid

@Composable
fun PagingHoneycombScreen(viewModel: MyViewModel) {
    val lazyPagingItems = viewModel.pagingDataFlow.collectAsLazyPagingItems()

    Honeycomb2DPagingGrid(
        items = lazyPagingItems,
        itemSize = 96.dp,
        spacingX = 8.dp,
        spacingY = 8.dp
    ) { item, index, modifier ->
        // Render item
    }
}
```

---

## ⚙️ Customization & API

| Parameter | Type | Default | Description |
|---|---|---|---|
| `items` | `List<T>` / `LazyPagingItems<T>` | *Required* | Data items collection |
| `columns` | `Int` | `0` | Number of columns (`0` for auto-calculation based on item count) |
| `itemSize` | `Dp` | `96.dp` | Cell diameter/size |
| `spacingX` | `Dp` | `8.dp` | Horizontal spacing between cells |
| `spacingY` | `Dp` | `8.dp` | Vertical spacing between cells |
| `maxScale` | `Float` | `1.15f` | Cell scale when at the viewport center |
| `minScale` | `Float` | `0.45f` | Cell scale when near viewport edges |
| `maxAlpha` | `Float` | `1.0f` | Cell opacity at the viewport center |
| `minAlpha` | `Float` | `0.20f` | Cell opacity near viewport edges |
| `itemContent` | `@Composable` | *Required* | Composable layout for each cell item |

### Included Shapes
- `PointyHexagonShape`: Hexagon with pointy top and bottom vertices.
- `FlatHexagonShape`: Hexagon with flat top and bottom edges.

---

## 📄 License

```text
Copyright 2026 RJ Fahad

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
