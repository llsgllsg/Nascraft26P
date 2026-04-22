package me.bounser.nascraft.database.commands.resources;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalisedDateTest {

    @Test
    void formatDateTime_producesIsoLikePattern() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 3, 17, 14, 5, 9);

        assertEquals("2024-03-17 14:05:09", NormalisedDate.formatDateTime(dateTime));
    }

    @Test
    void parseDateTime_roundTripsFormatOutput() {
        LocalDateTime original = LocalDateTime.of(2025, 11, 2, 23, 59, 0);

        String formatted = NormalisedDate.formatDateTime(original);

        assertEquals(original, NormalisedDate.parseDateTime(formatted));
    }

    @Test
    void parseDateTime_throwsOnInvalidInput() {
        assertThrows(
            java.time.format.DateTimeParseException.class,
            () -> NormalisedDate.parseDateTime("not-a-date")
        );
    }

    @Test
    void getDays_returnsDaysSinceEpochDate() {
        int expected = (int) ChronoUnit.DAYS.between(
            LocalDate.of(2023, 1, 1),
            LocalDate.now()
        );

        assertEquals(expected, NormalisedDate.getDays());
    }

    @Test
    void getDateFromDay_reconstructsDateFromDayOffset() {
        Date day100 = NormalisedDate.getDateFromDay(100);

        LocalDate reconstructed = day100.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();

        assertEquals(LocalDate.of(2023, 1, 1).plusDays(100), reconstructed);
    }

    @Test
    void getDateFromDay_forDayZeroReturnsEpochDate() {
        Date day0 = NormalisedDate.getDateFromDay(0);

        LocalDate reconstructed = day0.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();

        assertEquals(LocalDate.of(2023, 1, 1), reconstructed);
    }
}
