package iot.example.h2experiments

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import org.flywaydb.core.Flyway
import iot.example.h2experiments.job.BackendSyncController
import iot.example.h2experiments.job.PrintingController
import iot.example.h2experiments.migration.FileMigrationManager
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Logger.getLogger

object App {
    @JvmStatic
    fun main(args: Array<String>) {


        try {
            logger.info("Starting app")
            val injector = Kodein {
                import(httpClientModule)
                import(configModule)
                import(schedulerModule)
                import(storageModule)
                import(utilModule)
                import(jobModule)
                import(fileMigrationModule)
            }       

            //Если миграция не прошла успешно, то вылетит исключение и уложит приложение.
            logger.info("Starting database migration")
            val migrationManager: Flyway = injector.instance()
            migrationManager.migrate()

            logger.info("Database migration completed successfully")

            logger.info("Starting migration from file")
            val fileMigrationManager: FileMigrationManager = injector.instance()
            fileMigrationManager.doMigration()
            logger.info("Migration from file completed successfully.")

            logger.info("Starting jobs")

            //H2 tool connection after server start
//            Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start()

            val printingController = injector.instance<PrintingController>()
                    .apply(PrintingController::start)
            val backendSyncController = injector.instance<BackendSyncController>()
                    .apply(BackendSyncController::start)
            logger.info("Started!")

            val scheduler = injector.instance<ScheduledExecutorService>()

            Runtime.getRuntime().addShutdownHook(Thread {
                printingController.shutdown()
                backendSyncController.shutdown()
                scheduler.shutdown()
//                Server.shutdownTcpServer("tcp://localhost:9092", "", true, true)
            })
        } catch (t: Throwable) {
            logger.errToSenrty("Error while init App.", t)
        }
    }

    private val logger = getLogger()
}