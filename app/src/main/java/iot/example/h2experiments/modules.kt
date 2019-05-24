package iot.example.h2experiments

import android.system.Os.bind
import ch.qos.logback.classic.Level
import com.github.salomonbrys.kodein.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcConnectionPool
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.h2.H2DatabasePlugin
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import org.jdbi.v3.jodatime2.JodaTimePlugin
import org.joda.time.DateTime
import org.xml.sax.Parser
import iot.example.h2experiments.storage.*
import iot.example.h2experiments.storage.domain.CloseRequest
import iot.example.h2experiments.storage.domain.Line
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections.singleton
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

val httpClientModule = Kodein.Module {

    bind<HttpApiFactory>() with singleton { HttpApiFactory(kodein) }


    bind<BoxApi>() with singleton {
        val config = getConfig()
        val url = config.getString("api.box.url")
        kodein.instance<HttpApiFactory>()
                .createApiClient(BoxApi::class.java, url, true, Level.INFO) {
                    it.connectTimeout(config.getDuration("api.box.connectTimeout").toMillis(), TimeUnit.MILLISECONDS)
                        .writeTimeout(config.getDuration("api.box.writeTimeout").toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(config.getDuration("api.box.readTimeout").toMillis(), TimeUnit.MILLISECONDS)
                }
    }


}

val jobModule = Kodein.Module {
    bind<IterativeJobFactory>() with provider {
        IterativeJobFactory(instance())
    }
    bind<BackendSyncController>() with singleton {
        val cfg = getConfig().getConfig("job.backendSync")
        BackendSyncController(iterativeJobFactory = instance(),
                saleRepo = instance(),
                closeRepo = instance(),
                correctionsRepo = instance(),
                backend = instance(),
                errorRepo = instance(),
                fallbackDelay = cfg.getDuration("fallbackDelay"),
                idleDelay = cfg.getDuration("idleDelay"),
                initialDelay = cfg.getDuration("initialDelay"),
                tokenRefreshDelay = cfg.getDuration("tokenRefreshDelay"))
    }


}

val fileMigrationModule = Kodein.Module {
    bind<FileManager>() with factory { path: Path ->
        FileManager(path)
    }

    bind<Parser>() with singleton {
        Parser(gson = instance())
    }

    bind<FileMigrationManager>() with singleton {
        val cfg = getConfig()
        val storage = cfg.getString("fileMigration.storageFile").let { Paths.get(it) }
        val factory: (Path) -> FileManager = factory()
        FileMigrationManager(
                tokenRepo = instance(),
                parser = instance(),
                storageFileManager = factory(storage))
    }
}

val configModule = Kodein.Module {
    bind<Config>() with singleton { ConfigFactory.load() }
}

val schedulerModule = Kodein.Module {
    bind<ScheduledExecutorService>() with eagerSingleton {
        val threads = getConfig().getInt("job.workerThreads")
        Executors.newScheduledThreadPool(threads)
    }
    bind<Scheduler>() with eagerSingleton { Schedulers.from(kodein.instance<ScheduledExecutorService>()) }
}

val storageModule = Kodein.Module {
    bind<DataSource>() with singleton {
        val config = kodein.getConfig().getConfig("storage")
        val url = config.getString("url")
        val username = config.getString("username")
        val password = config.getString("password")

        JdbcConnectionPool.create(url, username, password)
    }

    bind<Jdbi>() with singleton {
        val ds: DataSource = kodein.instance()

        Jdbi.create(ds)
                .registerRowMapper(ConstructorMapper.factory(CloseRequest::class.java))
                .registerRowMapper(ConstructorMapper.factory(Line::class.java))
                .installPlugin(JodaTimePlugin())
                .installPlugin(H2DatabasePlugin())
    }

    bind<Flyway>() with provider { Flyway().apply { dataSource = kodein.instance() } }

    bind<TokenRepository>() with singleton { TokenRepository(kodein.instance()) }

    bind<CloseRepository>() with singleton { CloseRepository(kodein.instance()) }
    bind<SaleRepository>() with singleton { SaleRepository(kodein.instance()) }
    bind<CorrectionRepository>() with singleton { CorrectionRepository(kodein.instance()) }
    bind<ErrorRepository>() with singleton { ErrorRepository(kodein.instance()) }
}

val utilModule = Kodein.Module {
    bind<Gson>() with singleton {
        GsonBuilder()
                .serializeNulls()
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(object : TypeToken<DateTime>() {}.type, GsonDateTimeConverter())
                .create()
    }
}