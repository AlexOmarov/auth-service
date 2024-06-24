package ru.somarov.auth.infrastructure.kafka

data class Result(val code: Code) {
    enum class Code { OK, FAILED }
}
