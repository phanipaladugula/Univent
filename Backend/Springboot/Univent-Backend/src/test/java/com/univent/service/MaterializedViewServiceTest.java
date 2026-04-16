package com.univent.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class MaterializedViewServiceTest {

    @Test
    void fetchNewsAutomaticallyDelegatesToRssFeedService() {
        RssFeedService rssFeedService = mock(RssFeedService.class);
        MaterializedViewService service = new MaterializedViewService(rssFeedService);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        service.fetchNewsAutomatically();

        verify(rssFeedService).fetchAndStoreNews();
    }
}
