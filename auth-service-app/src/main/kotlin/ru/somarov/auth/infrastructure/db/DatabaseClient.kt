package ru.somarov.auth.infrastructure.db

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.api.PostgresTransactionDefinition
import io.r2dbc.spi.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.flywaydb.core.Flyway
import reactor.core.scheduler.Schedulers
import ru.somarov.auth.infrastructure.props.AppProps
import ru.somarov.auth.infrastructure.props.DbProps
import kotlin.time.toJavaDuration

class DatabaseClient(props: AppProps, registry: MeterRegistry, observationRegistry: ObservationRegistry) {
    private val logger = logger { }
    val factory: ConnectionFactory = createFactory(props.db, registry, props.name)

    @Suppress("kotlin:S6518", "TooGenericExceptionCaught") // Cannot replace with index accessor
    suspend fun <T> execute(
        query: String,
        params: Map<String, String>,
        mapper: (result: Row, metadata: RowMetadata) -> T
    ): List<T> {
        val connection = factory.create().awaitSingle()
        val res = try {
            val statement = connection.createStatement(query)
            params.forEach { (key, value) -> statement.bind(key, value) }
            statement.execute().asFlow()
                .map { it.map { row, meta -> mapper(row, meta) }.awaitFirstOrNull() }.filterNotNull().toList()
        } catch (e: Throwable) {
            logger.error(e) {
                "Got error while trying to perform sql query: query - " +
                    "$query, params - $params, ex - ${e.message}"
            }
            throw e
        } finally {
            connection.close().awaitFirstOrNull()
        }
        return res
    }

    @Suppress("kotlin:S6518", "TooGenericExceptionCaught") // Cannot replace with index accessor
    suspend fun <T> transactional(
        query: String,
        params: Map<String, String>,
        isolationLevel: IsolationLevel = IsolationLevel.READ_COMMITTED,
        mapper: (result: Row, metadata: RowMetadata) -> T
    ): List<T> {
        val connection = factory.create().awaitSingle()
        val res = try {
            connection.beginTransaction(PostgresTransactionDefinition.from(isolationLevel)).awaitFirstOrNull()
            val statement = connection.createStatement(query)
            params.forEach { (key, value) -> statement.bind(key, value) }
            val result = statement.execute().asFlow()
                .map { it.map { row, meta -> mapper(row, meta) }.awaitFirstOrNull() }.filterNotNull().toList()
            connection.commitTransaction()
            result
        } catch (e: Throwable) {
            logger.error(e) {
                "Got error while trying to perform transactional sql query: query - " +
                    "$query, params - $params, ex - ${e.message}"
            }
            throw e
        } finally {
            connection.commitTransaction()
            connection.close().awaitFirstOrNull()
        }
        return res
    }

    @Suppress("kotlin:S6518", "TooGenericExceptionCaught") // Cannot replace with index accessor
    suspend fun <T> transactional(
        isolationLevel: IsolationLevel = IsolationLevel.READ_COMMITTED,
        action: suspend (connection: Connection) -> T
    ): T {
        val connection = factory.create().awaitSingle()
        val res = try {
            connection.beginTransaction(PostgresTransactionDefinition.from(isolationLevel)).awaitSingle()
            val result = action(connection)
            connection.commitTransaction()
            result
        } catch (e: Throwable) {
            logger.error(e) { "Got error while trying to perform transactional action" }
            throw e
        } finally {
            connection.commitTransaction()
            connection.close().awaitFirstOrNull()
        }
        return res
    }

    private fun createFactory(props: DbProps, registry: MeterRegistry, name: String): ConnectionFactory {

        val configuration = Flyway.configure()
            .dataSource(
                "jdbc:postgresql://${props.host}:${props.port}/${props.name}?" +
                    "currentSchema=${props.schema}&prepareThreshold=0",
                props.user,
                props.password
            )
            .locations("classpath:db/migration")
        val flyway = Flyway(configuration)

        flyway.migrate()

        val pgConfig = PostgresqlConnectionConfiguration.builder()
            .host(props.host)
            .port(props.port)
            .database(props.name)
            .schema(props.schema)
            .username(props.user)
            .password(props.password)
            .applicationName(name)
            .autodetectExtensions(true)
            .connectTimeout(props.connectionTimeout.toJavaDuration())
            .statementTimeout(props.statementTimeout.toJavaDuration())
            .preparedStatementCacheQueries(0)
            .build()
        val factory = PostgresqlConnectionFactory(pgConfig)

        val scheduler = Schedulers.boundedElastic()

        val poolName = "${name}_pool"

        val conf = ConnectionPoolConfiguration.builder()
            .name(poolName)
            .maxSize(props.pool.maxSize)
            .allocatorSubscribeOn(scheduler)
            .connectionFactory(factory)
            .minIdle(props.pool.minIdle)
            .maxIdleTime(props.pool.maxIdleTime.toJavaDuration())
            .maxLifeTime(props.pool.maxLifeTime.toJavaDuration())
            .registerJmx(true)
            .validationDepth(ValidationDepth.REMOTE)
            .validationQuery(props.pool.validationQuery)
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
