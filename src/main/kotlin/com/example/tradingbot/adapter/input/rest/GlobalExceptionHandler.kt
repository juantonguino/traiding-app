package com.example.tradingbot.adapter.input.rest

import com.example.tradingbot.adapter.input.rest.dto.ErrorResponse
import com.example.tradingbot.domain.exception.EmergencyActiveException
import com.example.tradingbot.domain.exception.InvalidDataException
import com.example.tradingbot.domain.exception.NoOpenTradeException
import com.example.tradingbot.domain.exception.NotFoundException
import com.example.tradingbot.domain.exception.SignalsDisabledException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NoOpenTradeException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noOpenTrade(e: NoOpenTradeException, request: HttpServletRequest) =
        error(HttpStatus.NOT_FOUND.value(), "NO_OPEN_TRADE", e.message ?: "No open trade", request)

    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: NotFoundException, request: HttpServletRequest) =
        error(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", e.message ?: "Not found", request)

    @ExceptionHandler(EmergencyActiveException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun emergency(e: EmergencyActiveException, request: HttpServletRequest) =
        error(HttpStatus.CONFLICT.value(), "EMERGENCY_ACTIVE", e.message ?: "Emergency active", request)

    @ExceptionHandler(SignalsDisabledException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun signalsDisabled(e: SignalsDisabledException, request: HttpServletRequest) =
        error(HttpStatus.CONFLICT.value(), "SIGNALS_DISABLED", e.message ?: "Signals disabled", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun validation(e: MethodArgumentNotValidException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST.value(), "INVALID_ARGUMENT", "Invalid request body", request)

    @ExceptionHandler(InvalidDataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidData(e: InvalidDataException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST.value(), "INVALID_ARGUMENT", e.message ?: "Invalid data", request)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun illegalArgument(e: IllegalArgumentException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST.value(), "INVALID_ARGUMENT", e.message ?: "Invalid argument", request)

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun internal(e: Exception, request: HttpServletRequest): ErrorResponse {
        logger.error("Unhandled error on {} {}", request.method, request.requestURI, e)
        return error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "Internal error", request)
    }

    private fun error(status: Int, code: String, message: String, request: HttpServletRequest): ErrorResponse =
        ErrorResponse(
            timestamp = Instant.now(),
            status = status,
            code = code,
            message = message,
            requestId = request.getAttribute("requestId") as? String,
        )
}
