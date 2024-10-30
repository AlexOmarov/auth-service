package ru.somarov.auth.infrastructure.lib.db

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.r2dbc.postgresql.api.PostgresTransactionDefinition
import io.r2dbc.spi.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle

class DatabaseClient(private val factory: ConnectionFactory) {
    private val logger = logger { }

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
            statement.execute()
                .asFlow()
                .map { it.map { row, meta -> mapper(row, meta) }.awaitFirstOrNull() }
                .filterNotNull()
                .toList()
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
            val result = statement.execute()
                .asFlow()
                .map { it.map { row, meta -> mapper(row, meta) }.awaitFirstOrNull() }
                .filterNotNull()
                .toList()
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
}
