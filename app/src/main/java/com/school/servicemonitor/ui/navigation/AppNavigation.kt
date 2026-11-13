package com.school.servicemonitor.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Menu
import com.school.servicemonitor.ui.components.FloatingNavBar
import com.school.servicemonitor.ui.components.NavItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.school.servicemonitor.ui.settings.SettingsScreen
import com.school.servicemonitor.ui.reports.ReportsScreen
import com.school.servicemonitor.ui.activity.ActivityLogScreen
import com.school.servicemonitor.ui.deadlines.ActionRequiredScreen
import com.school.servicemonitor.ui.history.HistoryScreen
import com.school.servicemonitor.ui.attempts.AttemptScreen
import java.time.LocalDate
import com.school.servicemonitor.ui.dashboard.DashboardScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.school.servicemonitor.ui.sanctions.AssignSanctionScreen
import com.school.servicemonitor.ui.sanctions.ViolationFormScreen
import com.school.servicemonitor.ui.sanctions.ViolationListScreen
import com.school.servicemonitor.ui.students.StudentFormScreen
import com.school.servicemonitor.ui.students.StudentListScreen
import com.school.servicemonitor.ui.students.StudentProfileScreen

object Routes {
    const val MORE = "more"
    const val ACTIVITY_LOG = "activity_log"
    const val ACTION_REQUIRED = "action_required"
    const val DASHBOARD = "dashboard"
    const val STUDENTS = "students"
    const val SANCTIONS = "sanctions"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val STUDENT_PROFILE = "student/{id}"
    const val STUDENT_FORM = "student_form/{id}"          // id = 0 means "new student"
    const val VIOLATION_FORM = "violation_form/{id}"      // id = 0 means "new violation"
    const val ASSIGN_SANCTION = "assign_sanction/{studentId}"

    const val ATTEMPT = "attempt/{studentId}/{date}"

    fun attempt(studentId: Long, date: LocalDate) = "attempt/$studentId/$date"
    fun studentProfile(id: Long) = "student/$id"
    fun studentForm(id: Long) = "student_form/$id"
    fun violationForm(id: Long) = "violation_form/$id"
    fun assignSanction(studentId: Long) = "assign_sanction/$studentId"
}

private val navItems = listOf(
    NavItem(Routes.DASHBOARD, "Dashboard", Icons.Default.Home),
    NavItem(Routes.STUDENTS, "Students", Icons.Default.Person),
    NavItem(Routes.SANCTIONS, "Sanctions", Icons.Default.Warning),
    NavItem(Routes.HISTORY, "History", Icons.Default.DateRange),
    NavItem(Routes.MORE, "More", Icons.Default.Menu, setOf(Routes.REPORTS, Routes.SETTINGS))
)
private val barRoutes = navItems.map { it.route }.toSet() + Routes.REPORTS + Routes.SETTINGS
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in barRoutes

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (showBottomBar) {
                FloatingNavBar(
                    items = navItems,
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding)
        ) {

            composable(Routes.MORE) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Text("More", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Card(
                        onClick = { navController.navigate(Routes.REPORTS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Reports (PDF and Word)",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(
                        onClick = { navController.navigate(Routes.SETTINGS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Settings and Backup",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenEntry = { id, date -> navController.navigate(Routes.attempt(id, date)) },
                    onOpenActionRequired = { navController.navigate(Routes.ACTION_REQUIRED) }
                )
            }
            composable(Routes.ACTION_REQUIRED) {
                ActionRequiredScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStudent = { id -> navController.navigate(Routes.studentProfile(id)) }
                )
            }

            composable(
                route = Routes.ATTEMPT,
                arguments = listOf(
                    navArgument("studentId") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType }
                )
            ) { entry ->
                val id = entry.arguments?.getLong("studentId") ?: 0L
                val date = entry.arguments?.getString("date")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                AttemptScreen(
                    studentId = id,
                    date = date,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(Routes.studentProfile(id)) }
                )
            }

            composable(Routes.STUDENTS) {
                StudentListScreen(
                    onOpenStudent = { id -> navController.navigate(Routes.studentProfile(id)) },
                    onAddStudent = { navController.navigate(Routes.studentForm(0L)) }
                )
            }

            composable(Routes.SANCTIONS) {
                ViolationListScreen(
                    onOpenViolation = { id -> navController.navigate(Routes.violationForm(id)) },
                    onAddViolation = { navController.navigate(Routes.violationForm(0L)) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenStudent = { id -> navController.navigate(Routes.studentProfile(id)) },
                    onOpenActivityLog = { navController.navigate(Routes.ACTIVITY_LOG) }
                )
            }

            composable(Routes.ACTIVITY_LOG) {
                ActivityLogScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStudent = { id -> navController.navigate(Routes.studentProfile(id)) }
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            composable(
                route = Routes.STUDENT_PROFILE,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                StudentProfileScreen(
                    studentId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.studentForm(id)) },
                    onAssignSanction = { navController.navigate(Routes.assignSanction(id)) }
                )
            }

            composable(
                route = Routes.STUDENT_FORM,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                StudentFormScreen(
                    studentId = id,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.VIOLATION_FORM,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ViolationFormScreen(
                    violationId = id,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ASSIGN_SANCTION,
                arguments = listOf(navArgument("studentId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("studentId") ?: 0L
                AssignSanctionScreen(
                    studentId = id,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }
}