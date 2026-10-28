package com.example.mygarage

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.FirebaseStorage
import java.util.*

// CONFIGURARE VIZUALĂ
val MovSelman = Color(0xFF674EA7)
val GriFundal = Color(0xFFF8F9FA)
val AlbastruSteag = Color(0xFF003399)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = GriFundal) {
                    EcranPrincipalCloud()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranPrincipalCloud() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var user by remember { mutableStateOf(auth.currentUser) }
    var listaVehicule by remember { mutableStateOf(listOf<Masina>()) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var textCautare by remember { mutableStateOf("") }
    var vehiculDeEditat by remember { mutableStateOf<Masina?>(null) }
    var categorieSelectata by remember { mutableStateOf("Toate") }

    // SINCRONIZARE DATE + CURĂȚARE LA LOGOUT
    LaunchedEffect(user) {
        if (user != null) {
            db.collection("masini").whereEqualTo("ownerId", user?.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        listaVehicule = snapshot.documents.mapNotNull { it.toObject<Masina>()?.apply { id = it.id } }
                    }
                }
        } else {
            // Când userul este null (Logout), lista se golește obligatoriu
            listaVehicule = emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // HEADER
        Box(modifier = Modifier.fillMaxWidth().background(MovSelman).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountCircle,
                    null,
                    modifier = Modifier.size(45.dp).clickable { if (user == null) showLoginDialog = true },
                    tint = Color.White
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("SELMANTRANS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(user?.email ?: "Loghează-te pentru sincronizare", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                if (user != null) {
                    IconButton(onClick = {
                        auth.signOut()
                        user = null // Această schimbare va declanșa golirea listei în LaunchedEffect
                    }) {
                        Icon(Icons.Default.Logout, null, tint = Color.White)
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            FormularAdaugareCloud(onSave = { noua ->
                val id = db.collection("masini").document().id
                db.collection("masini").document(id).set(noua.copy(id = id, ownerId = user?.uid ?: ""))
            })

            Spacer(modifier = Modifier.height(16.dp))

            // FILTRARE (Auto, Camion, Trailer)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Toate", "Auto", "Camion", "Trailer").forEach { cat ->
                    FilterChip(
                        selected = categorieSelectata == cat,
                        onClick = { categorieSelectata = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MovSelman, selectedLabelColor = Color.White)
                    )
                }
            }

            OutlinedTextField(
                value = textCautare,
                onValueChange = { textCautare = it.uppercase() },
                placeholder = { Text("Caută număr...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MovSelman) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MovSelman, focusedLabelColor = MovSelman)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                val filtrata = listaVehicule.filter {
                    it.nrInmatriculare.contains(textCautare, ignoreCase = true) &&
                            (categorieSelectata == "Toate" || it.tipVehicul == categorieSelectata)
                }
                items(filtrata) { item ->
                    CardVehiculCloud(
                        v = item,
                        onSterge = { db.collection("masini").document(item.id).delete() },
                        onEdit = { vehiculDeEditat = item }
                    )
                }
            }
        }
    }

    if (showLoginDialog) DialogLoginCloud(onSucces = { user = auth.currentUser }, onDismiss = { showLoginDialog = false })

    vehiculDeEditat?.let { masina ->
        DialogEditareCloud(masina = masina, onDismiss = { vehiculDeEditat = null }, onConfirm = { updated ->
            db.collection("masini").document(masina.id).set(updated)
            vehiculDeEditat = null
        })
    }
}

@Composable
fun CardVehiculCloud(v: Masina, onEdit: () -> Unit, onSterge: () -> Unit) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, Color.Black),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if(v.tipVehicul == "Camion") "🚛" else if(v.tipVehicul == "Auto") "🚗" else "🏗️", fontSize = 24.sp)
                Surface(modifier = Modifier.padding(start = 8.dp).height(30.dp).width(20.dp), color = AlbastruSteag) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("★", color = Color.Yellow, fontSize = 5.sp)
                        Text("RO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                    }
                }
                Text(v.nrInmatriculare, modifier = Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MovSelman) }
                IconButton(onClick = onSterge) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InfoTagCloud("RCA", v.dataExpirareRCA)
                InfoTagCloud("ITP", v.dataExpirareITP)
                InfoTagCloud("ROV", v.dataExpirareRovinieta)
            }
            if (v.tipVehicul == "Camion") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InfoTagCloud("TAHO", v.dataExpirareTahograf)
                    InfoTagCloud("COPIE", v.dataExpirareCopiaConforma)
                }
            }

            Row {
                v.uriPdfRca?.let { url ->
                    TextButton(onClick = { openUrl(ctx, url) }) {
                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                        Text(" RCA", fontSize = 10.sp, color = Color.Red)
                    }
                }
                v.uriPdfCopia?.let { url ->
                    TextButton(onClick = { openUrl(ctx, url) }) {
                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                        Text(" COPIE", fontSize = 10.sp, color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoTagCloud(label: String, data: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(data.ifEmpty { "---" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FormularAdaugareCloud(onSave: (Masina) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.Add, null, tint = MovSelman)
                Text(if(expanded) " RESTRÂNGE" else " ADAUGĂ VEHICUL NOU", color = MovSelman, fontWeight = FontWeight.Bold)
            }
            if (expanded) FormularContinutCloud(onSave = { onSave(it); expanded = false }, textButon = "SALVEAZĂ")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormularContinutCloud(masinaEdit: Masina? = null, textButon: String, onSave: (Masina) -> Unit) {
    val ctx = LocalContext.current
    var nr by remember { mutableStateOf(masinaEdit?.nrInmatriculare ?: "") }
    var tip by remember { mutableStateOf(masinaEdit?.tipVehicul ?: "Auto") }
    var dRca by remember { mutableStateOf(masinaEdit?.dataExpirareRCA ?: "") }
    var dItp by remember { mutableStateOf(masinaEdit?.dataExpirareITP ?: "") }
    var dRov by remember { mutableStateOf(masinaEdit?.dataExpirareRovinieta ?: "") }
    var dTaho by remember { mutableStateOf(masinaEdit?.dataExpirareTahograf ?: "") }
    var dCopia by remember { mutableStateOf(masinaEdit?.dataExpirareCopiaConforma ?: "") }
    var urlRca by remember { mutableStateOf(masinaEdit?.uriPdfRca) }
    var urlCopia by remember { mutableStateOf(masinaEdit?.uriPdfCopia) }
    var uploading by remember { mutableStateOf(false) }

    val pickRca = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        u?.let { uploading = true; uploadFile(it, "rca_$nr") { url -> urlRca = url; uploading = false } }
    }
    val pickCopia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        u?.let { uploading = true; uploadFile(it, "copia_$nr") { url -> urlCopia = url; uploading = false } }
    }

    Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = nr, onValueChange = { nr = it.uppercase() }, label = { Text("Nr. Inmatriculare") }, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Auto", "Camion", "Trailer").forEach { t ->
                FilterChip(
                    selected = tip == t,
                    onClick = { tip = t },
                    label = { Text(t) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MovSelman, selectedLabelColor = Color.White)
                )
            }
        }

        FlowRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CampDataCloud("RCA", dRca) { dRca = it }
            CampDataCloud("ITP", dItp) { dItp = it }
            CampDataCloud("ROV", dRov) { dRov = it }
            if (tip == "Camion") {
                CampDataCloud("TAHO", dTaho) { dTaho = it }
                CampDataCloud("COPIE", dCopia) { dCopia = it }
            }
        }

        Row {
            TextButton(onClick = { if(nr.isNotEmpty()) pickRca.launch("application/pdf") }) {
                Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp), tint = MovSelman)
                Text(if(urlRca == null) " PDF RCA" else " RCA ✅", fontSize = 11.sp, color = Color.DarkGray)
            }
            if (tip == "Camion") {
                TextButton(onClick = { if(nr.isNotEmpty()) pickCopia.launch("application/pdf") }) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp), tint = MovSelman)
                    Text(if(urlCopia == null) " PDF COPIE" else " COPIE ✅", fontSize = 11.sp, color = Color.DarkGray)
                }
            }
        }
        if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MovSelman)
        Button(
            onClick = { if (nr.isNotEmpty()) onSave(Masina(nrInmatriculare = nr, tipVehicul = tip, dataExpirareRCA = dRca, dataExpirareITP = dItp, dataExpirareRovinieta = dRov, dataExpirareTahograf = dTaho, dataExpirareCopiaConforma = dCopia, uriPdfRca = urlRca, uriPdfCopia = urlCopia)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MovSelman)
        ) { Text(textButon) }
    }
}

@Composable
fun CampDataCloud(label: String, data: String, onSel: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = Calendar.getInstance()
    val d = DatePickerDialog(ctx, { _, y, m, day -> onSel("$day/${m+1}/$y") }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    Button(
        onClick = { d.show() },
        colors = ButtonDefaults.buttonColors(containerColor = MovSelman.copy(alpha = 0.1f), contentColor = MovSelman),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(if(data.isEmpty()) label else data, fontSize = 10.sp)
    }
}

@Composable
fun DialogLoginCloud(onSucces: () -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var eroare by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if(!loading) onDismiss() },
        title = { Text("Autentificare Cloud", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if(eroare.isNotEmpty()) {
                    // Verde pentru succes/info, Roșu pentru erori
                    val esteSucces = eroare.contains("verifică", ignoreCase = true) || eroare.contains("trimis", ignoreCase = true)
                    Text(
                        text = eroare,
                        color = if(esteSucces) Color(0xFF2E7D32) else Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Parolă") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                // BUTON RESETARE PAROLĂ
                TextButton(
                    onClick = {
                        if (email.isEmpty()) {
                            eroare = "Introdu email-ul pentru resetare!"
                        } else {
                            loading = true
                            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    loading = false
                                    eroare = "Ți-am trimis un link de resetare! Verifică email-ul (și SPAM)."
                                }
                                .addOnFailureListener {
                                    loading = false
                                    eroare = "Eroare: Email-ul nu a fost găsit."
                                }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("AI UITAT PAROLA?", color = MovSelman, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MovSelman)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // ÎNREGISTREAZĂ
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            if (email.isEmpty() || pass.isEmpty()) { eroare = "Completează toate câmpurile!"; return@TextButton }
                            loading = true
                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)
                                .addOnSuccessListener { result ->
                                    result.user?.sendEmailVerification()?.addOnCompleteListener {
                                        loading = false
                                        eroare = "Cont creat! Verifică email-ul (și SPAM) pentru confirmare."
                                        FirebaseAuth.getInstance().signOut()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    loading = false
                                    eroare = if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException) "Email deja înregistrat!"
                                    else "Eroare la înregistrare!"
                                }
                        }
                    ) { Text("ÎNREGISTREAZĂ", color = MovSelman, fontWeight = FontWeight.Bold) }

                    // LOGIN
                    Button(
                        enabled = !loading,
                        onClick = {
                            if (email.isEmpty() || pass.isEmpty()) { eroare = "Completează toate câmpurile!"; return@Button }
                            loading = true
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
                                .addOnSuccessListener { result ->
                                    val user = result.user
                                    if (user != null && user.isEmailVerified) {
                                        onSucces()
                                        onDismiss()
                                    } else {
                                        loading = false
                                        eroare = "Confirmă email-ul înainte! (Verifică și în SPAM)"
                                        FirebaseAuth.getInstance().signOut()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    loading = false
                                    eroare = when (e) {
                                        is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Email inexistent!"
                                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Parolă greșită!"
                                        else -> "Eroare la autentificare!"
                                    }
                                }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MovSelman),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("LOGIN") }
                }

                TextButton(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onDismiss) {
                    Text("ANULEAZĂ", color = Color.Gray)
                }
            }
        }
    )
}
fun uploadFile(uri: Uri, name: String, onOk: (String) -> Unit) {
    val ref = FirebaseStorage.getInstance().reference.child("uploads/$name")
    ref.putFile(uri).addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { onOk(it.toString()) } }
}

fun openUrl(ctx: Context, url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

@Composable
fun DialogEditareCloud(masina: Masina, onDismiss: () -> Unit, onConfirm: (Masina) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, title = { Text("Editează Vehicul") },
        text = { Box(Modifier.heightIn(max = 450.dp)) { FormularContinutCloud(masinaEdit = masina, textButon = "ACTUALIZEAZĂ", onSave = { onConfirm(it.copy(id = masina.id, ownerId = masina.ownerId)) }) } }
    )
}