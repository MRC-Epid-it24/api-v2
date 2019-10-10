package uk.ac.ncl.openlab.intake24.db

import com.typesafe.config.ConfigFactory
import org.flywaydb.core.Flyway

fun main() {
    val config = ConfigFactory.load()

    val flyway = Flyway.configure().dataSource(
            config.getString("db.system.url"),
            config.getString("db.system.user"),
            if (config.hasPath("db.system.user")) config.getString("db.system.user") else null)
            .table("flyway_migrations")
            .baselineVersion("87")
            .baselineOnMigrate(true)
            .locations("db/system/migration", "uk/ac/ncl/openlab/intake24/db/foods/migration")
            .load()

    flyway.migrate()
}
