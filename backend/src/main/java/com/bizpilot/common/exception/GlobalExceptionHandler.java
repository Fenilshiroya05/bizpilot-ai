package com.bizpilot.common.exception;

import com.bizpilot.common.response.ApiError;
import com.bizpilot.crm.exception.CustomerArchivedException;
import com.bizpilot.crm.exception.CustomerNotFoundException;
import com.bizpilot.crm.exception.DuplicateCustomerException;
import com.bizpilot.crm.exception.InvalidCustomerDataException;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.exception.EmailAlreadyExistsException;
import com.bizpilot.products.exception.DuplicateSkuException;
import com.bizpilot.products.exception.InvalidProductCategoryException;
import com.bizpilot.products.exception.InvalidProductDataException;
import com.bizpilot.products.exception.ProductCategoryNotFoundException;
import com.bizpilot.products.exception.ProductNotFoundException;
import com.bizpilot.sales.exception.InvalidAssigneeException;
import com.bizpilot.sales.exception.InvalidLeadDataException;
import com.bizpilot.sales.exception.LeadArchivedException;
import com.bizpilot.sales.exception.LeadNotFoundException;
import com.bizpilot.security.exception.AccountNotActiveException;
import com.bizpilot.security.exception.InvalidCredentialsException;
import com.bizpilot.security.exception.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handling foundation. Business-specific exceptions and
 * mappings are added in later phases as those modules are implemented.
 * Internal error details are never exposed to clients (see docs/security.md).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                       HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError body = ApiError.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Invalid request",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                                HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Invalid request",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(EmailAlreadyExistsException ex,
                                                               HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT.value(),
                "EMAIL_ALREADY_EXISTS",
                "An account with this email already exists",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                               HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiError> handleAccountNotActive(AccountNotActiveException ex,
                                                             HttpServletRequest request) {
        String code = ex.getStatus() == UserStatus.LOCKED ? "ACCOUNT_LOCKED" : "ACCOUNT_DISABLED";
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                code,
                "This account cannot currently sign in",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                                HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "INVALID_REFRESH_TOKEN",
                "Invalid refresh token",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequestBody(HttpMessageNotReadableException ex,
                                                                HttpServletRequest request) {
        // Covers malformed JSON and invalid enum values (e.g. an unrecognized
        // customer status string) deserializing a request body — without this,
        // Jackson's deserialization failure would otherwise fall through to the
        // generic 500 handler below instead of a client-caused 400.
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Invalid request",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> handleCustomerNotFound(CustomerNotFoundException ex,
                                                             HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "CUSTOMER_NOT_FOUND",
                "Customer not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiError> handleDuplicateCustomer(DuplicateCustomerException ex,
                                                              HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_CUSTOMER",
                "A customer with this email already exists in this organization",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CustomerArchivedException.class)
    public ResponseEntity<ApiError> handleCustomerArchived(CustomerArchivedException ex,
                                                             HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT.value(),
                "CUSTOMER_ARCHIVED",
                "This customer is archived and cannot be modified",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCustomerDataException.class)
    public ResponseEntity<ApiError> handleInvalidCustomerData(InvalidCustomerDataException ex,
                                                                HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_CUSTOMER_DATA",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<ApiError> handleLeadNotFound(LeadNotFoundException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "LEAD_NOT_FOUND",
                "Lead not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(LeadArchivedException.class)
    public ResponseEntity<ApiError> handleLeadArchived(LeadArchivedException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT.value(),
                "LEAD_ARCHIVED",
                "This lead is archived and cannot be modified",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidAssigneeException.class)
    public ResponseEntity<ApiError> handleInvalidAssignee(InvalidAssigneeException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ASSIGNEE",
                "The specified assignee does not exist in this organization",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidLeadDataException.class)
    public ResponseEntity<ApiError> handleInvalidLeadData(InvalidLeadDataException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_LEAD_DATA",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "PRODUCT_NOT_FOUND",
                "Product not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ProductCategoryNotFoundException.class)
    public ResponseEntity<ApiError> handleProductCategoryNotFound(ProductCategoryNotFoundException ex,
                                                                    HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "PRODUCT_CATEGORY_NOT_FOUND",
                "Product category not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiError> handleDuplicateSku(DuplicateSkuException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_SKU",
                "A product with this SKU already exists in this organization",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidProductCategoryException.class)
    public ResponseEntity<ApiError> handleInvalidProductCategory(InvalidProductCategoryException ex,
                                                                   HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PRODUCT_CATEGORY",
                "The specified product category does not exist in this organization",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidProductDataException.class)
    public ResponseEntity<ApiError> handleInvalidProductData(InvalidProductDataException ex,
                                                               HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PRODUCT_DATA",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "The requested resource was not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // Covers both URL-level (RestAccessDeniedHandler's filter-chain path) and
        // method-level (@PreAuthorize -> AuthorizationDeniedException, a subtype)
        // denials — the latter is thrown *inside* the MVC dispatch, so it reaches
        // this @RestControllerAdvice before it would ever reach the filter chain.
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "You do not have permission to access this resource",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing request {}", request.getRequestURI(), ex);
        ApiError body = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
