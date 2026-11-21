package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.DuplicateCar;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.MarkError;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.agreements.AgreementError;
import io.github.rkaushikdev.drawbarline.audit.AuditError;
import io.github.rkaushikdev.drawbarline.blocking.BlockingError;
import io.github.rkaushikdev.drawbarline.clearance.ClearanceError;
import io.github.rkaushikdev.drawbarline.clock.ClockError;
import io.github.rkaushikdev.drawbarline.customers.CustomerError;
import io.github.rkaushikdev.drawbarline.days.CalendarError;
import io.github.rkaushikdev.drawbarline.distribution.DistributionError;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoError;
import io.github.rkaushikdev.drawbarline.fleet.CarRecordError;
import io.github.rkaushikdev.drawbarline.fleet.EquipmentError;
import io.github.rkaushikdev.drawbarline.fleet.OwnershipError;
import io.github.rkaushikdev.drawbarline.fleet.StencilError;
import io.github.rkaushikdev.drawbarline.hazmat.HazmatError;
import io.github.rkaushikdev.drawbarline.interchange.ReconcileError;
import io.github.rkaushikdev.drawbarline.interchange.SheetError;
import io.github.rkaushikdev.drawbarline.lading.LadingError;
import io.github.rkaushikdev.drawbarline.measure.MeasureError;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import io.github.rkaushikdev.drawbarline.money.MoneyError;
import io.github.rkaushikdev.drawbarline.network.NetworkError;
import io.github.rkaushikdev.drawbarline.persistence.StoreError;
import io.github.rkaushikdev.drawbarline.scales.ScaleError;
import io.github.rkaushikdev.drawbarline.switching.SwitchError;
import io.github.rkaushikdev.drawbarline.train.ConsistError;
import io.github.rkaushikdev.drawbarline.util.Literal;
import io.github.rkaushikdev.drawbarline.waybill.WaybillError;
import io.github.rkaushikdev.drawbarline.weight.WeightError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turning a domain refusal into a status code.
 *
 * <p>The domain raises one of a few dozen errors and says nothing about HTTP, which is right. This
 * is the one table that decides what each of them means over the wire, and it is a table rather
 * than a chain of type checks so that adding an error class without deciding its status is visible
 * rather than silently a five hundred.
 *
 * <p>Every refusal comes back the same shape: the stable code, the message, and the context the
 * domain attached. A client that wants to tell one refusal from another reads the code, never the
 * prose. A request that is not the shape its route takes is not a refusal of that kind: it comes
 * back unprocessable with a {@code detail} list saying what was wrong and where.
 */
@RestControllerAdvice
public final class Errors {

    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int UNPROCESSABLE = 422;
    public static final int BAD_REQUEST = 400;

    /**
     * The status of every error class the domain raises. A class without a line falls back to a
     * bad request, which is the reading somebody forgot rather than the reading somebody chose.
     */
    public static final Map<Class<? extends DrawbarlineError>, Integer> STATUS_FOR = statuses();

    private static final String NOT_A_NUMBER = "Input should be a valid integer";

    private static Map<Class<? extends DrawbarlineError>, Integer> statuses() {
        Map<Class<? extends DrawbarlineError>, Integer> status = new LinkedHashMap<>();
        status.put(UnknownCar.class, NOT_FOUND);
        status.put(NetworkError.class, NOT_FOUND);
        status.put(CustomerError.class, NOT_FOUND);
        status.put(DuplicateCar.class, CONFLICT);
        status.put(RegistryError.class, CONFLICT);
        status.put(EventError.class, CONFLICT);
        status.put(SettlementError.class, CONFLICT);
        status.put(OwnershipError.class, CONFLICT);
        status.put(EmbargoError.class, CONFLICT);
        status.put(SwitchError.class, CONFLICT);
        status.put(DistributionError.class, CONFLICT);
        status.put(ConsistError.class, UNPROCESSABLE);
        status.put(TariffError.class, UNPROCESSABLE);
        status.put(RateError.class, UNPROCESSABLE);
        status.put(BillingError.class, UNPROCESSABLE);
        status.put(MarkError.class, UNPROCESSABLE);
        status.put(CarRecordError.class, UNPROCESSABLE);
        status.put(EquipmentError.class, UNPROCESSABLE);
        status.put(StencilError.class, UNPROCESSABLE);
        status.put(LadingError.class, UNPROCESSABLE);
        status.put(SheetError.class, UNPROCESSABLE);
        status.put(ReconcileError.class, UNPROCESSABLE);
        status.put(HazmatError.class, UNPROCESSABLE);
        status.put(ClearanceError.class, UNPROCESSABLE);
        status.put(ScaleError.class, UNPROCESSABLE);
        status.put(BlockingError.class, UNPROCESSABLE);
        status.put(WaybillError.class, UNPROCESSABLE);
        status.put(MeasureError.class, UNPROCESSABLE);
        status.put(WeightError.class, UNPROCESSABLE);
        status.put(MoneyError.class, UNPROCESSABLE);
        status.put(MileageError.class, UNPROCESSABLE);
        status.put(ClockError.class, UNPROCESSABLE);
        status.put(CalendarError.class, UNPROCESSABLE);
        status.put(AgreementError.class, UNPROCESSABLE);
        status.put(AuditError.class, UNPROCESSABLE);
        // A request reaches the store's refusal with a value too large for the column it goes in.
        status.put(StoreError.class, UNPROCESSABLE);
        status.put(ConfigurationError.class, BAD_REQUEST);
        return Collections.unmodifiableMap(status);
    }

    /** The status one refusal deserves, walking up to the nearest decided class. */
    public static int statusFor(DrawbarlineError error) {
        for (Class<?> kind = error.getClass(); kind != null; kind = kind.getSuperclass()) {
            Integer found = STATUS_FOR.get(kind);
            if (found != null) {
                return found;
            }
        }
        return BAD_REQUEST;
    }

    /** The code, the message and the context, the context in key order with every value as text. */
    public static Map<String, Object> bodyOf(DrawbarlineError error) {
        Map<String, String> context = new LinkedHashMap<>();
        new TreeMap<>(error.context()).forEach((key, value) -> context.put(key, Literal.plain(value)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.code());
        body.put("message", error.message());
        body.put("context", context);
        return body;
    }

    @ExceptionHandler(DrawbarlineError.class)
    ResponseEntity<Map<String, Object>> refused(DrawbarlineError error) {
        return ResponseEntity.status(statusFor(error)).body(bodyOf(error));
    }

    @ExceptionHandler(RequestRejected.class)
    ResponseEntity<Map<String, Object>> rejected(RequestRejected rejected) {
        return unprocessable(rejected.problems());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException failure) {
        if (failure.getCause() instanceof JsonMappingException mapping) {
            return unprocessable(List.of(problem(mapping)));
        }
        if (failure.getCause() instanceof InputCoercionException) {
            return unprocessable(List.of(new Problem("int_type", List.of("body"), NOT_A_NUMBER)));
        }
        if (failure.getCause() instanceof JsonProcessingException) {
            return unprocessable(List.of(new Problem("json_invalid", List.of("body", 0), "JSON decode error")));
        }
        return unprocessable(List.of(new Problem(Problem.MISSING, List.of("body"), "Field required")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> notJson(HttpMediaTypeNotSupportedException failure) {
        return unprocessable(List.of(new Problem(
                "model_attributes_type",
                List.of("body"),
                "Input should be a valid dictionary or object to extract fields from")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException failure) {
        List<Problem> problems = new ArrayList<>();
        for (ObjectError error : failure.getBindingResult().getAllErrors()) {
            if (error.contains(ConstraintViolation.class)) {
                problems.add(problem(List.of("body"), error.unwrap(ConstraintViolation.class)));
            } else if (error instanceof FieldError field) {
                problems.add(new Problem(
                        Problem.VALUE_ERROR, List.of("body", snake(field.getField())), field.getDefaultMessage()));
            } else {
                problems.add(new Problem(Problem.VALUE_ERROR, List.of("body"), error.getDefaultMessage()));
            }
        }
        return unprocessable(problems);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> outOfRange(HandlerMethodValidationException failure) {
        List<Problem> problems = new ArrayList<>();
        failure.getParameterValidationResults().forEach(result -> {
            List<Object> where = List.of(rootOf(result.getMethodParameter()), nameOf(result.getMethodParameter()));
            result.getResolvableErrors().forEach(error -> {
                ConstraintViolation<?> violation = result.unwrap(error, ConstraintViolation.class);
                problems.add(new Problem(typeOf(violation), where, violation.getMessage()));
            });
        });
        return unprocessable(problems);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException failure) {
        return unprocessable(
                List.of(new Problem(Problem.MISSING, List.of("query", failure.getParameterName()), "Field required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> mismatched(MethodArgumentTypeMismatchException failure) {
        List<Object> where = List.of(rootOf(failure.getParameter()), nameOf(failure.getParameter()));
        Class<?> wanted = failure.getRequiredType();
        if (wanted == int.class || wanted == Integer.class) {
            return unprocessable(List.of(
                    new Problem("int_parsing", where, NOT_A_NUMBER + ", unable to parse string as an integer")));
        }
        if (wanted == boolean.class || wanted == Boolean.class) {
            return unprocessable(List.of(
                    new Problem("bool_parsing", where, "Input should be a valid boolean, unable to interpret input")));
        }
        if (wanted == LocalDate.class) {
            return unprocessable(List.of(new Problem("date_parsing", where, "Input should be a valid date")));
        }
        return unprocessable(List.of(new Problem(Problem.VALUE_ERROR, where, "Input should be a valid value")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> nowhere(NoResourceFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Not Found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> notAllowed(HttpRequestMethodNotSupportedException failure) {
        Set<String> allowed = failure.getSupportedHttpMethods() == null
                ? Set.of()
                : failure.getSupportedHttpMethods().stream()
                        .map(Object::toString)
                        .collect(Collectors.toCollection(TreeSet::new));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, String.join(", ", allowed))
                .body(Map.of("detail", "Method Not Allowed"));
    }

    private static ResponseEntity<Map<String, Object>> unprocessable(List<Problem> problems) {
        return ResponseEntity.status(UNPROCESSABLE)
                .body(Map.of("detail", problems.stream().map(Problem::asMap).toList()));
    }

    /** Where Jackson stopped reading a body and why, in the words a caller is shown. */
    static Problem problem(JsonMappingException mapping) {
        List<Object> where = new ArrayList<>();
        where.add("body");
        for (JsonMappingException.Reference step : mapping.getPath()) {
            if (step.getFieldName() != null) {
                where.add(step.getFieldName());
            } else if (step.getIndex() >= 0) {
                where.add(step.getIndex());
            }
        }
        if (mapping instanceof Refused refused) {
            return new Problem(refused.type(), where, refused.why());
        }
        if (mapping instanceof UnrecognizedPropertyException) {
            return new Problem(Problem.EXTRA, where, "Extra inputs are not permitted");
        }
        if (mapping instanceof MismatchedInputException mismatched) {
            return mismatch(where, mismatched.getTargetType());
        }
        return new Problem(Problem.VALUE_ERROR, where, mapping.getOriginalMessage());
    }

    private static Problem mismatch(List<Object> where, Class<?> wanted) {
        if (wanted == null) {
            return new Problem(Problem.VALUE_ERROR, where, "Input should be a valid value");
        }
        if (wanted == int.class || wanted == Integer.class || wanted == long.class || wanted == Long.class) {
            return new Problem("int_type", where, NOT_A_NUMBER);
        }
        if (wanted == boolean.class || wanted == Boolean.class) {
            return new Problem("bool_type", where, "Input should be a valid boolean");
        }
        if (wanted == String.class) {
            return new Problem("string_type", where, "Input should be a valid string");
        }
        if (wanted == LocalDate.class) {
            return new Problem("date_parsing", where, "Input should be a valid date");
        }
        if (Collection.class.isAssignableFrom(wanted)) {
            return new Problem("list_type", where, "Input should be a valid list");
        }
        return new Problem(
                "model_attributes_type", where, "Input should be a valid dictionary or object to extract fields from");
    }

    /**
     * A broken constraint on a body, located from {@code root} down: field names in snake case,
     * positions in a list as numbers. A rule about the body as a whole is located at the body.
     */
    static Problem problem(List<Object> root, ConstraintViolation<?> violation) {
        String type = typeOf(violation);
        List<Path.Node> steps = new ArrayList<>();
        violation.getPropertyPath().forEach(steps::add);
        List<Object> where = new ArrayList<>(root);
        for (int at = 0; at < steps.size(); at++) {
            Path.Node step = steps.get(at);
            if (step.getIndex() != null) {
                where.add(step.getIndex());
            }
            boolean wholeBody = at == steps.size() - 1 && type.equals(Problem.VALUE_ERROR);
            if (step.getName() != null && !wholeBody) {
                where.add(snake(step.getName()));
            }
        }
        String message = type.equals(Problem.MISSING) ? "Field required" : violation.getMessage();
        return new Problem(type, where, message);
    }

    private static String typeOf(ConstraintViolation<?> violation) {
        String constraint = violation
                .getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
        return switch (constraint) {
            case "NotNull" -> Problem.MISSING;
            case "Min" -> "greater_than_equal";
            case "Max" -> "less_than_equal";
            default -> Problem.VALUE_ERROR;
        };
    }

    private static String rootOf(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(PathVariable.class) ? "path" : "query";
    }

    private static String nameOf(MethodParameter parameter) {
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null && !query.name().isEmpty()) {
            return query.name();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        return snake(String.valueOf(parameter.getParameterName()));
    }

    /** A Java name as the wire writes it: {@code secondPass} becomes {@code second_pass}. */
    static String snake(String name) {
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int at = 0; at < name.length(); at++) {
            char one = name.charAt(at);
            if (Character.isUpperCase(one)) {
                out.append('_').append(Character.toLowerCase(one));
            } else {
                out.append(one);
            }
        }
        return out.toString();
    }
}
