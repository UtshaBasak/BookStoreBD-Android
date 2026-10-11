package io.github.utshabasak.bookstorebd.core.datastore

import javax.inject.Qualifier

/** The session's own file, kept out of backups. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionPreferences

/** Everyday settings, which may travel to a new phone. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsPreferences
