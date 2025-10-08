package com.sirim.scanner.data.repository

/** Thrown when attempting to persist a record with a duplicate primary key value. */
class DuplicateRecordException(message: String) : Exception(message)

/** Wrapper for unrecoverable database errors during persistence operations. */
class DatabaseException(message: String, cause: Throwable) : Exception(message, cause)
