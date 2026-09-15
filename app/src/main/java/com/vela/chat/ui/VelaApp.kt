package com.vela.chat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.ui.appearance.AppearanceScreen
import com.vela.chat.ui.chat.ChatScreen
import com.vela.chat.ui.drawer.ArchiveScreen
import com.vela.chat.ui.models.ModelDashboardScreen
import com.vela.chat.ui.navigation.Routes
import com.vela.chat.ui.parameters.ParametersScreen
import com.vela.chat.ui.profiles.ProfileEditScreen
import com.vela.chat.ui.profiles.ProfilesScreen
import com.vela.chat.ui.prompts.PromptLibraryScreen
import com.vela.chat.ui.search.GlobalSearchScreen
import com.vela.chat.ui.screens.tailscale.TailscaleScreen
import com.vela.chat.ui.settings.SecurityScreen
import com.vela.chat.ui.settings.SettingsScreen
import com.vela.chat.ui.theme.VelaTheme
import com.vela.chat.ui.theme.nova.NovaTokens
import com.vela.chat.ui.voice.VoiceScreen
import com.vela.chat.ui.websearch.WebSearchScreen

/**
 * Horizontal travel (as a fraction of the screen width) for Nova navigation —
 * a subtle push rather than a full-width slide.
 */
private const val NovaSlideFraction = 0.25f

/**
 * App root: wraps the whole tree in [VelaTheme] (Nova theme + legacy chat
 * locals) and hosts the single [NavHost]. The chat screen stays the start
 * destination; navigation uses Nova transitions (fade + subtle horizontal
 * slide, [NovaTokens.Motion.normal] with the emphasized/decelerate easings).
 *
 * The [Surface] is transparent so the background gradient provided by the
 * Nova theme is visible; [contentColor] is pinned explicitly because
 * `contentColorFor(Color.Transparent)` is unspecified.
 */
@Composable
fun VelaApp(settings: AppSettings) {
    VelaTheme(settings = settings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = Routes.CHAT_WITH_ARG,
                enterTransition = { novaEnter() },
                exitTransition = { novaExit() },
                popEnterTransition = { novaPopEnter() },
                popExitTransition = { novaPopExit() },
            ) {
                composable(
                    route = Routes.CHAT_WITH_ARG,
                    arguments = listOf(
                        navArgument(Routes.CONVERSATION_ID) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    ChatScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenConversation = { id ->
                            navController.navigate(Routes.chat(id)) {
                                popUpTo(Routes.CHAT_WITH_ARG) { inclusive = true }
                            }
                        },
                        onNewChat = {
                            navController.navigate(Routes.chat(null)) {
                                popUpTo(Routes.CHAT_WITH_ARG) { inclusive = true }
                            }
                        },
                        onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                        onOpenModels = { navController.navigate(Routes.MODELS) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                        onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                        onOpenParameters = { navController.navigate(Routes.PARAMETERS) },
                        onOpenPrompts = { navController.navigate(Routes.PROMPTS) },
                        onOpenWebSearch = { navController.navigate(Routes.WEB_SEARCH) },
                        onOpenVoice = { navController.navigate(Routes.VOICE) },
                        onOpenTailscale = { navController.navigate(Routes.TAILSCALE) },
                        onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                        onOpenModels = { navController.navigate(Routes.MODELS) },
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenPersonas = { navController.navigate(Routes.PERSONAS) },
                    )
                }

                // ---- Nova 2.0 screens ----

                composable(Routes.TAILSCALE) {
                    TailscaleScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.MODELS) {
                    ModelDashboardScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SEARCH) {
                    GlobalSearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                    )
                }

                composable(Routes.SECURITY) {
                    SecurityScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.PERSONAS) {
                    com.vela.chat.ui.personas.PersonasScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.ARCHIVE) {
                    ArchiveScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                    )
                }

                // ---- Existing sections ----

                composable(Routes.WEB_SEARCH) {
                    WebSearchScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.VOICE) {
                    VoiceScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.APPEARANCE) {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.PARAMETERS) {
                    ParametersScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.PROFILES) {
                    ProfilesScreen(
                        onBack = { navController.popBackStack() },
                        onEditProfile = { id -> navController.navigate(Routes.profileEdit(id)) },
                        onAddProfile = { navController.navigate(Routes.profileEdit()) },
                    )
                }

                composable(
                    route = Routes.PROFILE_EDIT_WITH_ARG,
                    arguments = listOf(
                        navArgument(Routes.PROFILE_ID) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    ProfileEditScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.PROMPTS) {
                    PromptLibraryScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/** Forward navigation: subtle slide in from the end + decelerating fade. */
private fun novaEnter(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { (it * NovaSlideFraction).toInt() },
        animationSpec = tween(NovaTokens.Motion.normal, easing = NovaTokens.Motion.emphasized),
    ) + fadeIn(tween(NovaTokens.Motion.normal, easing = NovaTokens.Motion.decelerate))

/** Forward navigation: the outgoing screen drifts toward the start while fading. */
private fun novaExit(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { -(it * NovaSlideFraction).toInt() },
        animationSpec = tween(NovaTokens.Motion.normal, easing = NovaTokens.Motion.emphasized),
    ) + fadeOut(tween(NovaTokens.Motion.fast, easing = NovaTokens.Motion.standard))

/** Back navigation: the incoming screen drifts in from the start. */
private fun novaPopEnter(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { -(it * NovaSlideFraction).toInt() },
        animationSpec = tween(NovaTokens.Motion.normal, easing = NovaTokens.Motion.emphasized),
    ) + fadeIn(tween(NovaTokens.Motion.normal, easing = NovaTokens.Motion.decelerate))

/** Back navigation: the outgoing screen slides back toward the end. */
private fun novaPopExit(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { (it * NovaSlideFraction).toInt() },
        animationSpec = tween(NovaTokens.Motion.normal, easing = NovaTokens.Motion.emphasized),
    ) + fadeOut(tween(NovaTokens.Motion.fast, easing = NovaTokens.Motion.standard))
