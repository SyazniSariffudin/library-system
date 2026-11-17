package dev.syaz.library.service;

import dev.syaz.library.dto.response.LoanResponse;
import dev.syaz.library.enums.LoanStatus;
import dev.syaz.library.exception.InvalidJsonPatchException;
import dev.syaz.library.exception.LoanPatchNotAllowedException;
import dev.syaz.library.exception.LoanPatchTestFailedException;

import com.flipkart.zjsonpatch.Jackson3JsonPatch;
import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class LoanPatch {
    public static final String MEDIA_TYPE = "application/json-patch+json";

    private static final Set<String> RFC_OPERATIONS = Set.of("add", "remove", "replace", "move", "copy", "test");
    private static final Set<String> SUPPORTED_OPERATIONS = Set.of("replace", "test");
    private static final String STATUS_PATH = "/status";

    private final JsonMapper jsonMapper;

    public LoanStatus apply(JsonNode patch, LoanResponse current) {
        validate(patch);
        try {
            // Work on a copy. Persist only after every operation has succeeded.
            JsonNode currentDocument = jsonMapper.valueToTree(current);
            JsonNode updatedDocument = Jackson3JsonPatch.apply(patch, currentDocument);
            return LoanStatus.valueOf(updatedDocument.path("status").asString());
        } catch (JsonPatchApplicationException exception) {
            throw new LoanPatchTestFailedException(exception);
        }
    }

    private void validate(JsonNode patch) {
        if (patch == null || !patch.isArray()) {
            throw new InvalidJsonPatchException("JSON Patch must be an array of operations.");
        }
        for (JsonNode operation : patch) {
            validateOperation(operation);
        }
    }

    private void validateOperation(JsonNode operation) {
        if (!operation.isObject() || !operation.path("op").isString() || !operation.path("path").isString()) {
            throw new InvalidJsonPatchException("Each operation must contain string op and path members.");
        }

        String operationName = operation.path("op").asString();
        String path = operation.path("path").asString();
        if (!RFC_OPERATIONS.contains(operationName)) {
            throw new InvalidJsonPatchException("Unknown JSON Patch operation.");
        }
        if (!SUPPORTED_OPERATIONS.contains(operationName) || !STATUS_PATH.equals(path)) {
            throw new LoanPatchNotAllowedException("Only replace and test on /status are supported.");
        }
        if (!operation.has("value")) {
            throw new InvalidJsonPatchException("Operation requires a value member.");
        }

        JsonNode value = operation.get("value");
        if ("replace".equals(operationName)
                && (!value.isString() || !LoanStatus.RETURNED.name().equals(value.asString()))) {
            throw new LoanPatchNotAllowedException("Status can only be replaced with RETURNED.");
        }
    }
}
