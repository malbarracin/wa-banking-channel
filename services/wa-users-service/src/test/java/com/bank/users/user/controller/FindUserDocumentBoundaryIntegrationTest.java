package com.bank.users.user.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FindUserDocumentBoundaryIntegrationTest {

    private final MockMvc mockMvc;
    private final UserRepository repository;

    FindUserDocumentBoundaryIntegrationTest(MockMvc mockMvc, UserRepository repository) {
        this.mockMvc = mockMvc;
        this.repository = repository;
    }

    @ParameterizedTest
    @CsvSource({"DNI, 1", "DNI, 64", "PASSPORT, 64", "LC, 64", "LE, 64", "CUIT, 64"})
    @DisplayName("Consulta todos los tipos de documento y admite los límites de longitud sin truncar")
    void findsDocumentAtAcceptedLengthBoundaries(DocumentType documentType, int documentLength) throws Exception {
        String documentNumber = "aB09".repeat(16).substring(0, documentLength);
        User user = new User();
        user.setDocumentType(documentType);
        user.setDocumentNumber(documentNumber);
        user.setDisplayName("Ana");
        user.setPhone("+5491112345678");
        user.setBirthDate(LocalDate.of(1990, 1, 2));
        repository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users")
                        .param("documentType", documentType.name())
                        .param("documentNumber", documentNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.documentType").value(documentType.name()))
                .andExpect(jsonPath("$.documentNumber").value(documentNumber))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(jsonPath("$.phone").value("+5491112345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-02"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.preferences").isMap())
                .andExpect(jsonPath("$.preferences").isEmpty())
                .andExpect(jsonPath("$.channelLinkEligible").value(true));
    }
}
