package com.school.servicemonitor.ui.navigation

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.school.servicemonitor.ui.components.PlaceholderScreen
import com.school.servicemonitor.ui.sanctions.AssignSanctionScreen
import com.school.servicemonitor.ui.sanctions.ViolationFormScreen
import com.school.servicemonitor.ui.sanctions.ViolationListScreen
import com.school.servicemonitor.ui.students.StudentFormScreen
import com.school.servicemonitor.ui.students.StudentListScreen
import com.school.servicemonitor.ui.students.StudentProfileScreen

object Routes {
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

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, "Dashboard", Icons.Default.Home),
    TopLevelDestination(Routes.STUDENTS, "Students", Icons.Default.Person),
    TopLevelDestination(Routes.SANCTIONS, "Sanctions", Icons.Default.Warning),
    TopLevelDestination(Routes.HISTORY, "History", Icons.Default.DateRange),
    TopLevelDestination(Routes.REPORTS, "Reports", Icons.Default.Info),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding)
        ) {
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
                PlaceholderScreen("Settings and Backup", "Settings and database backup arrive in Stage 18.")
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