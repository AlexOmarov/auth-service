package ru.somarov.auth.lib.db

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ValidationDepth
import org.flywaydb.core.Flyway
import reactor.core.scheduler.Schedulers
import ru.somarov.auth.lib.observability.ObservabilityRegistry
import kotlin.time.toJavaDuration

object ConnectionFactoryFactory {
    fun createFactory(props: DbProps, registry: ObservabilityRegistry, name: String): ConnectionPool {
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
        bindToMeterRegistry(pool, registry.meterRegistry, tags)

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
