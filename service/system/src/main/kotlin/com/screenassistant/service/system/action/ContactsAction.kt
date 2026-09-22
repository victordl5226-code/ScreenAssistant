package com.screenassistant.service.system.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_CONTACTS_DISPLAYED = 20
    }

    fun listContacts(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Error: No tengo permiso para leer tus contactos. Concede el permiso en ajustes."
        }

        val contacts = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                if (!name.isNullOrBlank()) {
                    contacts.add(name)
                }
            }
        }

        if (contacts.isEmpty()) {
            return "No tienes contactos guardados."
        }

        val displayed = contacts.take(MAX_CONTACTS_DISPLAYED)
        val list = displayed.joinToString(", ")
        val suffix = if (contacts.size > MAX_CONTACTS_DISPLAYED) {
            " y ${contacts.size - MAX_CONTACTS_DISPLAYED} más"
        } else {
            ""
        }

        return "Tienes ${contacts.size} contactos: $list$suffix"
    }
}
