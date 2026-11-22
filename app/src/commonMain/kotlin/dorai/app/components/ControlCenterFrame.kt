package dorai.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dorai.app.theme.ProductShapes
import dorai.app.theme.ProductStyle
import dorai.app.theme.ThemeMode
import kotlinx.coroutines.launch

data class WorkspaceSection(val id: String, val label: String, val number: String, val enabled: Boolean = true)

val matijaSections = listOf(
    WorkspaceSection("findings", "Change findings", "03"),
    WorkspaceSection("export", "Validation & export", "04"),
)

/** Who is signed in, shown in the account menu of the top bar. */
data class WorkspaceAccount(
    val username: String,
    val role: String,
    val server: String,
    val onLogout: () -> Unit,
    /** Back to the login page to pick another saved server, keeping this session; null hides the item. */
    val onSwitchServer: (() -> Unit)? = null,
)

/** Phones get a drawer, middle widths an icon rail, wide windows the grouped sidebar. */
private enum class NavMode { DRAWER, RAIL, SIDEBAR }

/** The sidebar groups; a section in none of them (a preview's own) goes under Workspace. */
private val sectionGroups = listOf(
    "Workspace" to listOf("inventory", "review", "findings"),
    "Register" to listOf("roi", "register", "export"),
    "Admin" to listOf("admin"),
)

private fun grouped(sections: List<WorkspaceSection>): List<Pair<String, List<WorkspaceSection>>> {
    val known = sectionGroups.flatMap { it.second }.toSet()
    return sectionGroups.mapIndexedNotNull { index, (title, ids) ->
        val members = sections.filter { it.id in ids || (index == 0 && it.id !in known) }
        if (members.isEmpty()) null else title to members
    }
}

/**
 * Shared frame of the workspace: navigation, one top bar and the section content.
 * [account] fills the account menu (language, theme, sign out); without it (previews) the bar shows
 * the language and theme buttons instead. Navigation stays a callback supplied by the host.
 */
@Composable
fun ControlCenterFrame(
    selected: String, onSection: (String) -> Unit,
    theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    sections: List<WorkspaceSection> = matijaSections,
    isDemo: Boolean = false,
    onLanguage: ((AppLanguage) -> Unit)? = null,
    account: WorkspaceAccount? = null,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    content: @Composable (String) -> Unit,
) {
    val colors = ProductStyle.colors
    val sectionState = rememberSaveableStateHolder()
    val notices = remember { SnackbarHostState() }
    Surface(modifier.fillMaxSize(), color = colors.background) {
        CompositionLocalProvider(LocalNotices provides notices) {
            BoxWithConstraints {
                val mode = when {
                    maxWidth < 600.dp -> NavMode.DRAWER
                    maxWidth < 1200.dp -> NavMode.RAIL
                    else -> NavMode.SIDEBAR
                }
                val gutter = when (mode) { NavMode.DRAWER -> 16.dp; NavMode.RAIL -> 24.dp; NavMode.SIDEBAR -> 32.dp }
                val drawer = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val pick: (String) -> Unit = { id -> onSection(id); scope.launch { drawer.close() } }
                // Back closes an open drawer before it walks the sections.
                backHandler(drawer.isOpen) { scope.launch { drawer.close() } }
                val main = @Composable {
                    Column(Modifier.fillMaxSize().background(lerp(colors.background, colors.panel, 0.18f))) {
                        TopBar(
                            title = sections.find { it.id == selected }?.label ?: "Workspace",
                            icon = sectionIcon(selected), gutter = gutter, isDemo = isDemo,
                            onMenu = if (mode == NavMode.DRAWER) ({ scope.launch { drawer.open() } }) else null,
                            account = account, theme = theme, onTheme = onTheme, onLanguage = onLanguage,
                        )
                        HorizontalDivider(color = colors.line.copy(alpha = 0.7f))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            SectionTransition(selected, Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(horizontal = gutter, vertical = 24.dp)) {
                                activeSection -> sectionState.SaveableStateProvider(activeSection) { content(activeSection) }
                            }
                        }
                    }
                }
                when (mode) {
                    NavMode.DRAWER -> ModalNavigationDrawer(
                        drawerState = drawer,
                        drawerContent = {
                            ModalDrawerSheet(Modifier.width(300.dp), drawerShape = ProductShapes.dialog,
                                drawerContainerColor = colors.panel) {
                                SidebarContent(sections, selected, pick, Modifier.padding(horizontal = 12.dp, vertical = 20.dp))
                            }
                        },
                        content = main,
                    )
                    NavMode.RAIL -> Row(Modifier.fillMaxSize()) {
                        Rail(sections, selected, onSection)
                        VerticalDivider(color = colors.line.copy(alpha = 0.7f))
                        Box(Modifier.weight(1f)) { main() }
                    }
                    NavMode.SIDEBAR -> Row(Modifier.fillMaxSize()) {
                        SidebarContent(sections, selected, onSection, Modifier.width(240.dp).fillMaxHeight().padding(horizontal = 14.dp, vertical = 20.dp))
                        VerticalDivider(color = colors.line.copy(alpha = 0.7f))
                        Box(Modifier.weight(1f)) { main() }
                    }
                }
                NoticeHost(notices, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Brand, then the sections in their groups: the wide sidebar and the phone drawer. */
@Composable
private fun SidebarContent(sections: List<WorkspaceSection>, selected: String, onSection: (String) -> Unit, modifier: Modifier) {
    val colors = ProductStyle.colors
    Column(modifier.verticalScroll(rememberScrollState())) {
        BrandMark(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Spacer(Modifier.height(28.dp))
        grouped(sections).forEach { (title, members) ->
            Text(uiText(title), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp), color = colors.muted,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                members.forEach { NavigationItem(it, selected == it.id, onSection) }
            }
        }
    }
}

/** A column of section icons with short labels, for middle widths and phones held sideways. */
@Composable
private fun Rail(sections: List<WorkspaceSection>, selected: String, onSection: (String) -> Unit) {
    val colors = ProductStyle.colors
    Column(Modifier.width(80.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BrandTile(36.dp)
        Spacer(Modifier.height(12.dp))
        sections.forEach { section ->
            val active = section.id == selected
            val label = uiText(section.label)
            Surface(onClick = { onSection(section.id) }, enabled = section.enabled, color = Color.Transparent,
                modifier = Modifier.width(72.dp).actionPointer(section.enabled).semantics { this.selected = active; contentDescription = label }) {
                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(width = 52.dp, height = 30.dp).background(if (active) colors.accent.copy(alpha = 0.16f) else Color.Transparent, ProductShapes.pill),
                        contentAlignment = Alignment.Center) {
                        LineIcon(sectionIcon(section.id), color = if (active) colors.accent else colors.muted, size = 18.dp)
                    }
                    Text(shortLabel(section.id, label), color = if (active) colors.ink else colors.muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The rail has room for about one word; the full name is its accessibility label. */
@Composable
private fun shortLabel(id: String, label: String): String = when (id) {
    "inventory" -> uiText("Inventory")
    "review" -> uiText("AI review")
    "findings" -> uiText("Findings")
    "export" -> uiText("Export")
    "roi" -> "RoI"
    "register" -> uiText("Items")
    "admin" -> uiText("Admin")
    else -> label
}

/** One 56 dp bar: the section's name, then the account menu (or language and theme in previews). */
@Composable
private fun TopBar(
    title: String, icon: ProductIcon, gutter: Dp, isDemo: Boolean, onMenu: (() -> Unit)?,
    account: WorkspaceAccount?, theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onLanguage: ((AppLanguage) -> Unit)?,
) {
    val colors = ProductStyle.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = if (onMenu != null) 4.dp else gutter, end = gutter),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onMenu != null) {
            val menuLabel = uiText("Open navigation")
            IconButton(onMenu, Modifier.actionPointer().semantics { contentDescription = menuLabel }) {
                LineIcon(ProductIcon.MENU, color = colors.ink, size = 20.dp)
            }
        } else LineIcon(icon, color = colors.muted, size = 18.dp)
        Text(uiText(title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isDemo) OutlineChip("Demo", colors.muted, dot = true)
        if (account != null) AccountMenu(account, theme, onTheme, onLanguage)
        else {
            if (onLanguage != null) LanguagePicker(onLanguage)
            ThemeToggle(theme, onTheme)
        }
    }
}

/** Avatar with the signed-in user's initials; its menu holds role, server, theme, language and sign out. */
@Composable
private fun AccountMenu(account: WorkspaceAccount, theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onLanguage: ((AppLanguage) -> Unit)?) {
    val colors = ProductStyle.colors
    var open by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val label = uiText("Account")
    Box {
        Surface(onClick = { open = true }, shape = CircleShape, color = colors.accent.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
            modifier = Modifier.size(36.dp).actionPointer().semantics { contentDescription = "$label ${account.username}" }) {
            Box(contentAlignment = Alignment.Center) {
                Text(account.username.take(2).uppercase(), color = colors.ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        DropdownMenu(open, { open = false }, shape = ProductShapes.card, containerColor = colors.panel,
            border = BorderStroke(1.dp, colors.line)) {
            Column(Modifier.widthIn(min = 240.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.username, style = MaterialTheme.typography.titleSmall)
                MetaText(uiText(account.role) + " · " + account.server.removePrefix("https://").removePrefix("http://"))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.line)
            MenuHeading("Theme")
            listOf(ThemeMode.SYSTEM to ("System" to ProductIcon.SYSTEM), ThemeMode.LIGHT to ("Light" to ProductIcon.SUN),
                ThemeMode.DARK to ("Dark" to ProductIcon.MOON)).forEach { (mode, look) ->
                MenuChoice(look.first, look.second, mode == theme) { onTheme(mode) }
            }
            if (onLanguage != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.line)
                MenuHeading(uiText(UiCopy.LANGUAGE))
                AppLanguage.entries.forEach { option -> MenuChoice(option.nativeName, ProductIcon.GLOBE, option == language) { onLanguage(option) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.line)
            account.onSwitchServer?.let { onSwitch ->
                DropdownMenuItem(
                    text = { Text(uiText("Switch server")) },
                    leadingIcon = { LineIcon(ProductIcon.CLOUD, color = colors.ink, size = 17.dp) },
                    onClick = { open = false; onSwitch() },
                    modifier = Modifier.actionPointer(),
                )
            }
            DropdownMenuItem(
                text = { Text(uiText("Log out")) },
                leadingIcon = { LineIcon(ProductIcon.LOGOUT, color = colors.ink, size = 17.dp) },
                onClick = { open = false; account.onLogout() },
                modifier = Modifier.actionPointer(),
            )
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(uiText(text), Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp), color = ProductStyle.colors.muted,
        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
}

@Composable
private fun MenuChoice(text: String, icon: ProductIcon, chosen: Boolean, onClick: () -> Unit) {
    val colors = ProductStyle.colors
    DropdownMenuItem(
        text = { Text(uiText(text), fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal) },
        leadingIcon = { LineIcon(icon, color = if (chosen) colors.accent else colors.muted, size = 17.dp) },
        trailingIcon = { if (chosen) LineIcon(ProductIcon.CHECK, color = colors.accent, size = 16.dp) else Spacer(Modifier.size(16.dp)) },
        onClick = onClick,
        modifier = Modifier.actionPointer().semantics { selected = chosen },
    )
}

/** Each section its own icon, the same in the sidebar, drawer, rail and top bar. */
private fun sectionIcon(id: String): ProductIcon = when (id) {
    "inventory" -> ProductIcon.CLOUD
    "review" -> ProductIcon.SPARK
    "findings" -> ProductIcon.SHIELD
    "export" -> ProductIcon.ARCHIVE
    "roi" -> ProductIcon.TABLE
    "register" -> ProductIcon.LIST
    "admin" -> ProductIcon.USERS
    else -> ProductIcon.FILE
}

@Composable
private fun NavigationItem(section: WorkspaceSection, active: Boolean, onSection: (String) -> Unit) {
    val colors = ProductStyle.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        if (active) colors.accent.copy(alpha = 0.12f) else if (hovered && section.enabled) colors.hover else Color.Transparent,
        tween(ProductMotion.FEEDBACK), label = "navigation-background")
    val foreground by animateColorAsState(
        if (!section.enabled) colors.muted.copy(alpha = 0.45f) else if (active) colors.ink else colors.muted,
        tween(ProductMotion.FEEDBACK), label = "navigation-label")
    Surface(onClick = { onSection(section.id) }, enabled = section.enabled, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().hoverable(interaction).actionPointer(section.enabled).semantics { selected = active },
        shape = ProductShapes.control, color = fill) {
        Row(Modifier.height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LineIcon(sectionIcon(section.id), color = if (active) colors.accent else foreground, size = 18.dp)
            Text(uiText(section.label), color = foreground, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

/** Light and dark switch for previews without an account menu. */
@Composable
private fun ThemeToggle(mode: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val angle by animateFloatAsState(if (dark) 0f else 90f, tween(ProductMotion.THEME), label = "theme-icon-rotation")
    val actionLabel = uiText(if (dark) UiCopy.LIGHT_ACTION else UiCopy.DARK_ACTION)
    val currentLabel = uiText(if (dark) UiCopy.DARK_THEME else UiCopy.LIGHT_THEME)
    OutlinedIconButton(
        onClick = { onTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
        modifier = Modifier.size(36.dp).actionPointer().semantics {
            contentDescription = actionLabel
            stateDescription = currentLabel
        },
        shape = ProductShapes.control,
        border = BorderStroke(1.dp, ProductStyle.colors.line),
    ) {
        LineIcon(if (dark) ProductIcon.MOON else ProductIcon.SUN,
            modifier = Modifier.graphicsLayer { rotationZ = angle }, color = ProductStyle.colors.ink, size = 17.dp)
    }
}
