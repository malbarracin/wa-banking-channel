package com.bcra.ws.users.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

import com.bcra.ws.users.entity.UserEntity;

/** Comprueba los índices creados al arrancar sin depender de Mongo. */
class UserIndexInitializerTest {

    @Test
    @DisplayName("Se crean dos índices únicos independientes con sus nombres estables")
    void shouldCreateIndependentUniqueIndexes() {
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations operations = mock(IndexOperations.class);
        when(template.indexOps(UserEntity.class)).thenReturn(operations);
        new UserIndexInitializer(template).run(null);
        ArgumentCaptor<IndexDefinition> indexes = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(operations, org.mockito.Mockito.times(2)).ensureIndex(indexes.capture());
        assertThat(indexes.getAllValues()).satisfiesExactly(document -> {
            assertThat(document.getIndexOptions()).containsEntry("name", "uk_document").containsEntry("unique", true);
            assertThat(document.getIndexKeys()).containsOnlyKeys("documentType", "documentNumber");
        }, email -> {
            assertThat(email.getIndexOptions()).containsEntry("name", "uk_email").containsEntry("unique", true);
            assertThat(email.getIndexKeys()).containsOnlyKeys("email");
        });
    }
}
