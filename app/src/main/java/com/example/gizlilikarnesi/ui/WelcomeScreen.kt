package com.example.gizlilikarnesi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gizlilikarnesi.ui.theme.AquaDeep
import com.example.gizlilikarnesi.ui.theme.HudLabel
import com.example.gizlilikarnesi.ui.theme.Ink

/**
 * Ilk acilista gosterilen karsilama ekrani. Kullanici VPN izni vermeden once
 * ne oldugunu, neden izin istendigini ve verilerinin nerede kaldigini bilsin.
 * Ana ekrandaki "Nasıl çalışır?" baglantisiyla tekrar acilabilir.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
                Text("GİZLİLİK KARNESİ", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text("HOŞ GELDİN", style = HudLabel.copy(color = AquaDeep))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Telefonundaki uygulamalar arka planda reklam ve analitik şirketlerine bağlanır. " +
                        "Gizlilik Karnesi bunları görünür kılar ve her uygulamaya A'dan F'ye bir not verir.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        item {
            WelcomePanel(
                label = "NE YAPAR?",
                points = listOf(
                    "Tarama sırasında uygulamaların hangi adreslere bağlandığını izler.",
                    "Bu adresleri bilinen takip şirketleriyle eşleştirir.",
                    "Şirketleri amacına göre gruplar ve sade bir özet çıkarır."
                )
            )
        }
        item {
            WelcomePanel(
                label = "NEDEN VPN İZNİ?",
                points = listOf(
                    "Android'de uygulama trafiğini görmenin root gerektirmeyen tek yolu bir VPN'dir.",
                    "Bu VPN yalnızca alan adı (DNS) sorgularını görür; web, video ve oyun trafiği normal yoldan gider.",
                    "İnternetin kesilmez. Tarama bitince VPN kendiliğinden kapanır."
                )
            )
        }
        item {
            WelcomePanel(
                label = "VERİLERİN NEREDE KALIR?",
                points = listOf(
                    "Her şey telefonunda işlenir; sonuçlar hiçbir sunucuya gönderilmez.",
                    "Telefona sertifika kurulmaz, root gerekmez, ayarların değiştirilmez.",
                    "Sonuçları yalnızca sen \"Dışa aktar\" dersen paylaşırsın."
                )
            )
        }
        item {
            WelcomePanel(
                label = "NEYİ GÖREMEZ?",
                points = listOf(
                    "Şifreli içeriği okumaz: verinin nereye gittiğini görür, içinde ne olduğunu görmez.",
                    "Bazı uygulamalar DNS'i atlayan yollar kullanabilir; bunlar görünmeyebilir."
                )
            )
        }
        item {
            GelButton("ANLADIM, BAŞLA", onClick = onContinue, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WelcomePanel(label: String, points: List<String>) {
    GlassPanel(label = label) {
        points.forEachIndexed { index, point ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium.copy(color = AquaDeep))
                Spacer(Modifier.width(8.dp))
                Text(point, style = MaterialTheme.typography.bodyMedium.copy(color = Ink))
            }
            if (index < points.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}
