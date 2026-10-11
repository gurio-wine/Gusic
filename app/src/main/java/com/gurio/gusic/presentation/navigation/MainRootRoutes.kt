package com.gurio.gusic.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gurio.gusic.R

internal data class MainRootTabSpec(
    @StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int,
    val screen: Screen,
)

internal val mainRootTabSpecs = listOf(
    MainRootTabSpec(R.string.tab_home, R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
    MainRootTabSpec(R.string.tab_online, R.drawable.rounded_headphones_24, R.drawable.rounded_headphones_24, Screen.LxMusic),
    // “我的”tab 图标与发现页“我的”模块按钮保持一致（person 图标）
    MainRootTabSpec(R.string.tab_library, R.drawable.rounded_person_24, R.drawable.rounded_person_24, Screen.Library),
)

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Search.route,
    Screen.Library.route,
    Screen.LxMusic.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.LxMusic.route -> 1
    Screen.Library.route -> 2
    Screen.Search.route -> 3
    else -> null
}
