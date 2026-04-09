package dk.mcmsm.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the lifecycle status of a modpack container.
 */
public enum PackStatus {
    NOT_DEPLOYED("not_deployed"),
    RUNNING("running"),
    STOPPED("stopped"),
    DEPLOY_FAILED("deploy_failed"),
    ERROR("error");

    private final String value;

    PackStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the wire-format value used in JSON serialization.
     *
     * @return lowercase status string matching the frontend contract
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolves a {@link PackStatus} from its wire-format string.
     *
     * @param value the status string (e.g. "running", "not_deployed")
     * @return the matching enum constant
     * @throws IllegalArgumentException if the value does not match any status
     */
    @JsonCreator
    public static PackStatus fromValue(String value) {
        for (var status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown pack status: " + value);
    }
}
