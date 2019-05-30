package uk.ac.ncl.intake24.storage

import com.google.inject.AbstractModule

class SimpleSharedStorageModule : AbstractModule() {

    override fun configure() {
        bind(SharedStorage::class.java).to(SharedStorageSimpleImpl::class.java)
    }
}