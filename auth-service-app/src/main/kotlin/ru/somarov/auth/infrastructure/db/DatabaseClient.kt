package ru.somarov.auth.infrastructure.db

import io.ktor.server.application.ApplicationEnvironment
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.MultiHostConnectionStrategy
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.api.PostgresTransactionDefinition
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Row
import io.r2dbc.spi.ValidationDepth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.flywaydb.core.Flyway
import reactor.core.scheduler.Schedulers
import kotlin.time.Duration
import kotlin.time.toJavaDuration


class DatabaseClient(env: ApplicationEnvironment, registry: MeterRegistry) {
    val factory: ConnectionFactory

    init {
        factory = createFactory(env, registry)
    }

    @Suppress("kotlin:S6518") // Cannot replace with index accessor
    suspend fun <T> executeActionInTransaction(
        function: suspend () -> T,
        isolationLevel: IsolationLevel = IsolationLevel.READ_COMMITTED
    ): T {
        val connection = factory.create().awaitSingle()
        connection.beginTransaction(PostgresTransactionDefinition.from(isolationLevel)).awaitSingle()
        val result = function.invoke()
        connection.commitTransaction()
        connection.releaseSavepoint("release") // how to release?
        return result
    }

    @Suppress("kotlin:S6518") // Cannot replace with index accessor
    suspend fun <T> executeQuery(
        query: String,
        params: Map<String, String>,
        mapper: (row: Row) -> T
    ): List<T> {
        val connection = factory.create().awaitSingle()
        val statement = connection
            .createStatement(query)
        params.forEach { (key, value) -> statement.bind(key, value) }

        val result = statement.execute()
            .asFlow()
            .map { it.map { row, _ ->
                mapper.invoke(row)
            }.awaitSingle() }
            .toList()
        return result
    }

    @Suppress("kotlin:S6518") // Cannot replace with index accessor
    suspend fun <T> executeQueryInTransaction(
        query: String,
        params: Map<String, String>,
        mapper: (row: Row) -> T,
        isolationLevel: IsolationLevel = IsolationLevel.READ_COMMITTED
    ): List<T> {
        val connection = factory.create().awaitSingle()
        connection.beginTransaction(PostgresTransactionDefinition.from(isolationLevel)).awaitSingle()
        val statement = connection
            .createStatement(query)
        params.forEach { (key, value) -> statement.bind(key, value) }

        val result = statement.execute()
            .asFlow()
            .map { it.map { row, _ ->
                mapper.invoke(row)
            }.awaitSingle() }
            .toList()
        connection.commitTransaction()
        connection.releaseSavepoint("release") // how to release?
        return result
    }

    private fun createFactory(env: ApplicationEnvironment, registry: MeterRegistry): ConnectionFactory {
        val host = env.config.property("application.db.host").getString()
        val port = env.config.property("application.db.port").getString().toInt()
        val db = env.config.property("application.db.name").getString()
        val schema = env.config.property("application.db.schema").getString()
        val user = env.config.property("application.db.user").getString()
        val password = env.config.property("application.db.password").getString()

        val configuration = Flyway.configure()
            .dataSource("jdbc:postgresql://${host}:${port}/${db}?currentSchema=${schema}&prepareThreshold=0", user, password)
            .locations("filesystem:db/migration")

        val flyway = Flyway(configuration)

        flyway.migrate()

        val pgConfig = PostgresqlConnectionConfiguration.builder()
            .host(host)
            .port(port)
            .database(db)
            .schema(schema)
            .username(user)
            .password(password)
            .applicationName(env.config.property("application.name").getString())
            .autodetectExtensions(true)
            .connectTimeout(
                Duration.parse(env.config.property("application.db.connection-timeout").getString()).toJavaDuration()
            )
            .targetServerType(MultiHostConnectionStrategy.TargetServerType.PRIMARY)
            .statementTimeout(
                Duration.parse(env.config.property("application.db.statement-timeout").getString()).toJavaDuration()
            )
            .preparedStatementCacheQueries(0)
            .build()

        val factory = PostgresqlConnectionFactory(pgConfig)

        val scheduler = Schedulers.boundedElastic()

        val poolName = "${env.config.property("application.name").getString()}_pool"

        val conf = ConnectionPoolConfiguration.builder()
            .name(poolName)
            .maxSize(env.config.property("application.db.pool.max-size").getString().toInt())
            .allocatorSubscribeOn(scheduler)
            .connectionFactory(factory)
            .minIdle(env.config.property("application.db.pool.min-idle").getString().toInt())
            .maxIdleTime(
                Duration.parse(env.config.property("application.db.pool.max-idle-time").getString())
                    .toJavaDuration()
            )
            .maxLifeTime(
                Duration.parse(env.config.property("application.db.pool.max-life-time").getString())
                    .toJavaDuration()
            )
            .registerJmx(true)
            .validationDepth(ValidationDepth.REMOTE)
            .validationQuery(env.config.property("application.db.pool.validation-query").getString())
            .build()

        val pool = ConnectionPool(conf)
        pool.warmup()

        @Suppress("SpreadOperator") // had to due to API of micrometer lib
        val tags = Tags.concat(Tags.empty(), *arrayOf("name", poolName))
        bindToMeterRegistry(pool, registry, tags)

        return pool
    }

    private fun bindToMeterRegistry(pool: ConnectionPool, registry: MeterRegistry, tags: Tags) {
        pool.metrics.ifPresent { metrics ->

            bindConnectionPoolMetric(
                registry,
                tags,
                Gauge.builder("r2dbc.pool.acquired", pool) { metrics.acquiredSize().toDouble() }
                    .description("Size of successfully acquired connections which are in active use.")
            )

            bindConnectionPoolMetric(
                registry,
                tags,
                Gauge.builder("r2dbc.pool.allocated", pool) { metrics.allocatedSize().toDouble() }
                    .description("Size of allocated connections in the pool which are in active use or idle.")
            )

            bindConnectionPoolMetric(
                registry,
                tags,
                Gauge.builder("r2dbc.pool.idle", pool) { metrics.idleSize().toDouble() }
                    .description("Size of idle connections in the pool.")
            )

            bindConnectionPoolMetric(
                registry,
                tags,
                Gauge.builder("r2dbc.pool.pending", pool) { metrics.pendingAcquireSize().toDouble() }
                    .description("Size of pending to acquire connections from the underlying connection factory.")
            )

            bindConnectionPoolMetric(
                registry,
                tags,
                Gauge.builder("r2dbc.pool.max-allocated", pool) { metrics.maxAllocatedSize.toDouble() }
                    .description("Maximum size of allocated connections that this pool allows.")
            )

            bindConnectionPoolMetric(
                registry,
                tags,
                Gauge.builder("r2dbc.pool.max-pending", pool) { metrics.maxPendingAcquireSize.toDouble() }
                    .description("Maximum size of pending state to acquire connections that this pool allows.")
            )
        }
    }

    private fun bindConnectionPoolMetric(registry: MeterRegistry, tags: Tags, builder: Gauge.Builder<*>) {
        builder.tags(tags).baseUnit("connections").register(registry)
    }
}
