package com.example.calendarapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.icu.util.TimeZone
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.calendarapp.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import org.joda.time.DateTime
import java.lang.Exception


class MainActivity : AppCompatActivity() {
    private val viewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private lateinit var googleSignInClient: GoogleSignInClient
    private val dangerousPermissions = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    private val EVENT_PROJECTION: Array<String> = arrayOf(
        CalendarContract.Calendars._ID,                     // 0
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 2
    )

    private val onDatePicked: (year: Int, month: Int, dayOfMonth: Int) -> (Unit) = { year, month, dayOfMonth ->
        viewModel.setDate(year, month, dayOfMonth)
    }
    private val onTimePicked: (pickedHour: Int, pickedMin: Int) -> (Unit) = { pickedHour, pickedMin ->
        viewModel.setTime(pickedMin, pickedHour)
        DatePickerFragment(onDatePicked).show(supportFragmentManager, "Date")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setListeners()
        setObservers()
        val permissions = dangerousPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (permissions.isNotEmpty()) requestPermissions(permissions, REQUEST_CODE_PERMISSION)
        val googleSignInOption = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOption)
    }

    override fun onStart() {
        super.onStart()
        val account = GoogleSignIn.getLastSignedInAccount(this)
        updateUi(account)
    }

    private fun setListeners() {
        binding.btnLogin.setOnClickListener { signIn() }
        binding.btnLogout.setOnClickListener { signOut() }
        binding.btnPickDate.setOnClickListener { TimePickerFragment(onTimePicked).show(supportFragmentManager, "Time") }
        binding.btnCreateSingleEvent.setOnClickListener { createSingleEvent() }
        binding.btnCreateSingleEventWithIntent.setOnClickListener { createSingleEventWithIntent() }
        binding.btnCreateRandomEvents.setOnClickListener { viewModel.generateRandomDates(limit = 10) }
    }

    private fun setObservers() {
        viewModel.dateLiveData.observe(this) {
            binding.tvDateValue.text = it.toString("dd-MM-yyyy : hh:mm")
        }
        viewModel.randomDatesLiveData.observe(this) {
            val ids = getCalendarIds()
            it.forEach { startDate ->
                ids.forEach { id -> pushEventToCalendar(id, startDate) }
            }
        }
    }

    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, SIGN_IN_REQ)
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnSuccessListener { updateUi(null) }
    }

    private fun updateUi(account: GoogleSignInAccount?) {
        if (account != null) {
            binding.tvUserInfo.text = account.displayName
            binding.eventsLayout.visibility = View.VISIBLE
            binding.loginLayout.visibility = View.GONE
        } else {
            binding.eventsLayout.visibility = View.GONE
            binding.loginLayout.visibility = View.VISIBLE
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        val account = task.result
        updateUi(account)
    }

    private fun createSingleEventWithIntent() {
        if (viewModel.dateLiveData.value == null) {
            showToast("Please specify date and time")
            return
        }
        val startDate = viewModel.dateLiveData.value!!
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startDate.millis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startDate.plusHours(1).millis)
            .putExtra(CalendarContract.Events.TITLE, "Testing Title")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Testing event at ${DateTime.now()}")
            .putExtra(Intent.EXTRA_EMAIL, "ali.rashad.dev@gmail.com")
        startActivity(intent)
    }

    private fun createSingleEvent() {
        if (viewModel.dateLiveData.value == null) {
            showToast("Please specify date and time")
            return
        }
        val ids = getCalendarIds()
        ids.forEach { pushEventToCalendar(it) }
    }

    private fun getCalendarIds(): List<String> {
        val idsList = mutableListOf<String>()
        val calendarUri: Uri = CalendarContract.Calendars.CONTENT_URI
        val cur: Cursor? = contentResolver.query(calendarUri, EVENT_PROJECTION, null, null)
        if (cur?.moveToFirst() == true) {
            while (cur.moveToNext()) {
                val idCol = cur.getColumnIndex(EVENT_PROJECTION[0])
                idsList.add(cur.getString(idCol))
            }
        }
        cur?.close()
        return idsList
    }

    private fun pushEventToCalendar(calId: String, inputDate: DateTime? = null) {
        val permissions = dangerousPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions, REQUEST_CODE_PERMISSION)
            return
        }
        val startDate = inputDate ?: viewModel.dateLiveData.value!!
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startDate.millis)
            put(CalendarContract.Events.DTEND, startDate.plusHours(1).millis)
            put(CalendarContract.Events.TITLE, "Testing Title")
            put(CalendarContract.Events.DESCRIPTION, "Testing event at ${DateTime.now()}")
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().displayName)
        }
        try {
            val data = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val attendeeValues = ContentValues().apply {
                put(CalendarContract.Attendees.ATTENDEE_NAME, "Trevor")
                put(CalendarContract.Attendees.ATTENDEE_EMAIL, "ali.rashad.dev@gmail.com")
                put(CalendarContract.Attendees.EVENT_ID, data?.lastPathSegment?.toLong())
            }
            contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, attendeeValues)
            showToast("Successfully created!")
        } catch (e: Exception) {
            showToast("Something went wrong, ${e.message}")
        }
    }

    enum class RandomCalendar {
        Day,
        Hour,
        Minute
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SIGN_IN_REQ) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            Log.d("MainActivity", "[${permissions.joinToString()}] is granted.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val SIGN_IN_REQ = 100
        const val REQUEST_CODE_PERMISSION = 0
    }
}