package uk.ac.ncl.openlab.intake24.db

import com.typesafe.config.ConfigFactory
import org.flywaydb.core.Flyway

fun main() {
    val config = ConfigFactory.load()

    val flyway = Flyway.configure().dataSource(
            config.getString("db.foods.url"),
            config.getString("db.foods.user"),
            if (config.hasPath("db.foods.password")) config.getString("db.foods.password") else null)
            .table("flyway_migrations")
            .baselineVersion("58")
            .baselineOnMigrate(true)
            .locations("db/foods/migration", "uk/ac/ncl/openlab/intake24/db/foods/migration")
            .load()

    flyway.migrate()
}
