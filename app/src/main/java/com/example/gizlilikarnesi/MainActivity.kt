package com.example.gizlilikarnesi

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.gizlilikarnesi.analysis.TrackerDatabase
import com.example.gizlilikarnesi.data.BlockList
import com.example.gizlilikarnesi.data.ScanStore
import com.example.gizlilikarnesi.analysis.ScanRecord
import com.example.gizlilikarnesi.ui.WelcomeScreen
import com.example.gizlilikarnesi.ui.AeroBackground
import com.example.gizlilikarnesi.analysis.ScanResults
import com.example.gizlilikarnesi.capture.TrafficVpnService
import com.example.gizlilikarnesi.ui.AppDetailScreen
import com.example.gizlilikarnesi.ui.HomeScreen
import com.example.gizlilikarnesi.ui.theme.GizlilikarnesiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tek bir tarama oturumunun suresi: bu sure sonunda VPN otomatik kapanir.
 * Internet tarama sirasinda kesilmedigi icin (bolum 18) kullaniciya rahatca
 * birkac uygulama acip kullanma firsati veren bir sure secildi.
 */
private const val SCAN_DURATION_SECONDS = 60

private const val KEY_WELCOME_DONE = "welcome_done"

class MainActivity : ComponentActivity() {

    private val scanning = mutableStateOf(false)

    /** Ilk acilista (ya da "Nasıl çalışır?" ile) gosterilen karsilama ekrani */
    private val showWelcome = mutableStateOf(false)

    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            beginScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWelcome.value = !prefs.getBoolean(KEY_WELCOME_DONE, false)
        BlockList.load(this)
        // Zemin her zaman aydinlik: durum cubugu simgeleri koyu olsun
        // (sistem karanlik modda olsa bile).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            GizlilikarnesiTheme {
                // Aero gradyan zemin en arkada; Scaffold saydam ki zemin gorunsun.
                AeroBackground {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        if (showWelcome.value) {
                            BackHandler(enabled = prefs.getBoolean(KEY_WELCOME_DONE, false)) {
                                showWelcome.value = false
                            }
                            WelcomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                onContinue = {
                                    prefs.edit().putBoolean(KEY_WELCOME_DONE, true).apply()
                                    showWelcome.value = false
                                }
                            )
                        } else {
                            PrivacyScorecardApp(
                                modifier = Modifier.padding(innerPadding),
                                scanning = scanning.value,
                                onStartClick = { requestVpnPermission() },
                                onStopClick = { endScan() },
                                onExportClick = { exportLastScan() },
                                onShowWelcome = { showWelcome.value = true },
                                onToggleBlock = { company -> BlockList.toggle(this@MainActivity, company) },
                                onOpenScan = { record ->
                                    ScanResults.publishRecord(record, TrackerDatabase.load(this@MainActivity))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        restoreLastScan()
    }

    /**
     * Uygulama yeniden acildiginda (bellekte sonuc yoksa) diskteki son
     * taramayi yukleyip ekrana getirir. Dosya okuma ana is parcacigini
     * bloklamasin diye IO'da yapilir.
     */
    private fun restoreLastScan() {
        lifecycleScope.launch(Dispatchers.IO) {
            ScanResults.setHistory(ScanStore.loadAll(this@MainActivity))
        }
        if (scanning.value || ScanResults.reports.value.isNotEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val record = ScanStore.loadLatest(this@MainActivity) ?: return@launch
            if (ScanResults.reports.value.isEmpty()) {
                ScanResults.publishRecord(record, TrackerDatabase.load(this@MainActivity))
            }
        }
    }

    /**
     * Son taramayi CSV'ye yazar ve Android'in "Paylas" menusunu acar
     * (e-posta, Drive, WhatsApp... kullanici secer). Dosya baska uygulamaya
     * FileProvider uzerinden, gecici okuma izniyle verilir.
     */
    private fun exportLastScan() {
        val record = ScanResults.lastScan.value ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    ScanStore.exportCsv(this@MainActivity, record, TrackerDatabase.load(this@MainActivity))
                }.getOrNull()
            }
            if (file == null) {
                Toast.makeText(this@MainActivity, "Dışa aktarma başarısız oldu", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Gizlilik Karnesi tarama sonuçları ${record.id}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Sonuçları paylaş"))
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            beginScan()
        }
    }

    private fun beginScan() {
        val intent = Intent(this, TrafficVpnService::class.java).apply {
            action = TrafficVpnService.ACTION_START
        }
        startForegroundService(intent)
        scanning.value = true
    }

    private fun endScan() {
        val intent = Intent(this, TrafficVpnService::class.java).apply {
            action = TrafficVpnService.ACTION_STOP
        }
        startService(intent)
        scanning.value = false
    }
}

/**
 * Uygulamanin en ust composable'i: hangi ekranin gosterilecegine karar verir.
 * Navigasyon kutuphanesi yerine tek bir durum degiskeni (secili paket adi)
 * kullaniyoruz: null ise ana ekran, dolu ise o uygulamanin detay ekrani.
 * Iki ekranli bir uygulama icin bu yeterli ve anlasilmasi kolay.
 */
@Composable
fun PrivacyScorecardApp(
    scanning: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onExportClick: () -> Unit,
    onShowWelcome: () -> Unit,
    onToggleBlock: (String) -> Unit,
    onOpenScan: (ScanRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    // Servisin yayinladigi karneler; her yeni domain'de liste kendiliginden guncellenir.
    val reports by ScanResults.reports.collectAsState()
    val lastScan by ScanResults.lastScan.collectAsState()
    val history by ScanResults.history.collectAsState()
    val blocked by BlockList.companies.collectAsState()

    // rememberSaveable: ekran donse bile hangi detayda oldugumuz kaybolmasin.
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }

    // Geri sayim sayaci composable icinde tutulur ki her saniye UI guncellensin.
    var remainingSeconds by rememberSaveable { mutableIntStateOf(SCAN_DURATION_SECONDS) }
    LaunchedEffect(scanning) {
        if (scanning) {
            remainingSeconds = SCAN_DURATION_SECONDS
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
            onStopClick()
        }
    }

    val selectedReport = reports.firstOrNull { it.packageName == selectedPackage }

    // Android'in geri tusu detaydan ana ekrana donsun (uygulamayi kapatmasin).
    BackHandler(enabled = selectedReport != null) { selectedPackage = null }

    if (selectedReport != null) {
        AppDetailScreen(
            report = selectedReport,
            onBack = { selectedPackage = null },
            modifier = modifier,
            // Tarama surerken zaman damgalari henuz kayda gecmedi: panel gizli kalir
            firstSeen = lastScan?.firstSeen?.get(selectedReport.packageName).orEmpty(),
            scanStartedAt = lastScan?.startedAtMillis,
            blockedCompanies = blocked,
            onToggleBlock = onToggleBlock
        )
    } else {
        HomeScreen(
            scanning = scanning,
            remainingSeconds = remainingSeconds,
            totalSeconds = SCAN_DURATION_SECONDS,
            reports = reports,
            onStartClick = onStartClick,
            onStopClick = onStopClick,
            onAppClick = { selectedPackage = it.packageName },
            modifier = modifier,
            lastScan = lastScan,
            onExportClick = onExportClick,
            history = history,
            onOpenScan = onOpenScan,
            onShowWelcome = onShowWelcome
        )
    }
}
