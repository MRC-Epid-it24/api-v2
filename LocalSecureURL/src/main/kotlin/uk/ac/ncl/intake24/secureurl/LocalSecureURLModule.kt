package uk.ac.ncl.intake24.secureurl

import com.google.inject.AbstractModule

class LocalSecureURLModule : AbstractModule() {

    override fun configure() {
        bind(SecureURLService::class.java).to(SecureURLLocalFileImpl::class.java)
        bind(LocalSecureURLCleanupDaemon::class.java).asEagerSingleton()
    }
}