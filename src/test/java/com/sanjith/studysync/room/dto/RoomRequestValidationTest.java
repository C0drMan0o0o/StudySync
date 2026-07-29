package com.sanjith.studysync.room.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoomRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        RoomRequest request = new RoomRequest("Room A", 4, "Floor 1");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void blankNameIsRejected() {
        RoomRequest request = new RoomRequest("", 4, "Floor 1");

        Set<ConstraintViolation<RoomRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void nonPositiveCapacityIsRejected() {
        RoomRequest request = new RoomRequest("Room A", 0, "Floor 1");

        Set<ConstraintViolation<RoomRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("capacity"));
    }

    @Test
    void blankLocationIsRejected() {
        RoomRequest request = new RoomRequest("Room A", 4, "");

        Set<ConstraintViolation<RoomRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("location"));
    }
}
