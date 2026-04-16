package com.univent.service;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class MaterializedViewServiceTest {

    @Test
    void fetchNewsAutomaticallyDelegatesToRssFeedService() {
        RssFeedService rssFeedService = mock(RssFeedService.class);
        DataSource dataSource = mock(DataSource.class);
        MaterializedViewService service = new MaterializedViewService(rssFeedService, dataSource);

        service.fetchNewsAutomatically();

        verify(rssFeedService).fetchAndStoreNews();
    }
}
