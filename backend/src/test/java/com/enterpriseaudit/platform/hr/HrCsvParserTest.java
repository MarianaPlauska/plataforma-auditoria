package com.enterpriseaudit.platform.hr;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrCsvParserTest {
    @Test
    void parsesOnlyMinimalEmploymentFields()
    {
        var people = HrCsvParser.parse("\uFEFFexternal_ref,unit_code,employment_status,effective_date\r\n"
                + "ref-01,\"unidade, sul\",active,2026-10-03\r\n"
                + "ref-02,unidade-norte,ON_LEAVE,");

        assertThat(people).hasSize(2);
        assertThat(people.getFirst().unitCode()).isEqualTo("unidade, sul");
        assertThat(people.getFirst().status()).isEqualTo("ACTIVE");
        assertThat(people.getFirst().effectiveDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(people.get(1).effectiveDate()).isNull();
    }

    @Test
    void rejectsDuplicateReferences()
    {
        assertThatThrownBy(() -> HrCsvParser.parse("external_ref,unit_code,employment_status,effective_date\n"
                + "ref-01,unidade-a,ACTIVE,\nref-01,unidade-b,ACTIVE,"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void rejectsUnexpectedColumnsThatCouldContainExcessData()
    {
        assertThatThrownBy(() -> HrCsvParser.parse("external_ref,unit_code,employment_status,effective_date,medical_record\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cabeçalho");
    }

    @Test
    void rejectsInvalidDatesAndUnclosedQuotes()
    {
        assertThatThrownBy(() -> HrCsvParser.parse("external_ref,unit_code,employment_status,effective_date\nref-01,unidade-a,ACTIVE,03/10/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAAA-MM-DD");
        assertThatThrownBy(() -> HrCsvParser.parse("external_ref,unit_code,employment_status,effective_date\nref-01,\"unidade,ACTIVE,"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aspa sem fechamento");
    }
}
