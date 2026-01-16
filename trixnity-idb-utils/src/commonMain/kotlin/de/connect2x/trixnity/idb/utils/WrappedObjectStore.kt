package de.connect2x.trixnity.idb.utils

import web.idb.IDBObjectStore

value class WrappedObjectStore(val store: IDBObjectStore) {
    companion object
}