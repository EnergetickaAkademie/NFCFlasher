package eu.swpelc.nfcflasher

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels // Required for by viewModels()
import eu.swpelc.nfcflasher.databinding.ActivityMainBinding // ViewBinding import
import eu.swpelc.nfcflasher.viewmodel.SharedViewModel // Import SharedViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // NFC specific variables
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var nfcIntentFilters: Array<IntentFilter>
    private lateinit var nfcTechLists: Array<Array<String>>

    // ViewModel for sharing NFC tag data with fragments
    private val sharedViewModel: SharedViewModel by viewModels()


    companion object {
        private const val TAG = "MainActivityNFC"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_read_nfc, R.id.nav_write_nfc, R.id.nav_config
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // NFC Setup
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
        } else if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable NFC in settings.", Toast.LENGTH_LONG).show()
        }

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlag)

        // Define intent filters
        val ndefIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                // Assuming your custom data type is text/plain for simplicity now
                // or a more specific one if you defined it.
                // addDataType("text/plain")
                // addDataType("application/vnd.nfcflasher.buildingid") // If using custom MIME
                addDataType("*/*") // Broad for now to catch more tags during debug
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                Log.e(TAG, "Malformed Mime type for NDEF", e)
                throw RuntimeException("Failed to add NDEF MIME type.", e)
            }
        }
        val techIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        nfcIntentFilters = arrayOf(ndefIntentFilter, techIntentFilter)
        nfcTechLists = arrayOf(arrayOf(Ndef::class.java.name))
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, nfcIntentFilters, nfcTechLists)
        Log.d(TAG, "Foreground dispatch enabled")
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        Log.d(TAG, "Foreground dispatch disabled")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "New Intent: ${intent.action}")
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == action
        ) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                Log.i(TAG, "NFC Tag detected: $tag")
                Toast.makeText(this, "NFC Tag Detected!", Toast.LENGTH_SHORT).show()
                sharedViewModel.setNfcTag(tag)
                // Log after calling setNfcTag
                Log.d(TAG, "Called sharedViewModel.setNfcTag with Tag: ${tag.toString()}")
            } else {
                Log.w(TAG, "Tag is null in onNewIntent")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
