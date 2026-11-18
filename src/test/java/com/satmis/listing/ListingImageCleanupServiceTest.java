package com.satmis.listing;

import com.satmis.enums.ListingImageStatus;
import com.satmis.storage.DeletedFile;
import com.satmis.storage.ObjectStorage;
import com.satmis.storage.StoredFilePurger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingImageCleanupServiceTest {

    private static final DeletedFile FIRST = new DeletedFile(1L, "listings/a.jpg");

    @Mock
    private ListingImageRepository listingImageRepository;
    @Mock
    private ObjectStorage objectStorage;

    private ListingImageCleanupService service;

    @BeforeEach
    void setUp() {
        ListingImageProperties properties = new ListingImageProperties(DataSize.ofMegabytes(10), 40,
                Duration.ofMinutes(10), Duration.ofHours(24));
        service = new ListingImageCleanupService(listingImageRepository, new StoredFilePurger(objectStorage),
                properties);
    }

    @Test
    void onImagesDeleted_removesFilesThenTheirRows() {
        service.onImagesDeleted(new ListingImagesDeletedEvent(List.of(FIRST)));

        verify(objectStorage).delete(FIRST.objectKey());
        verify(listingImageRepository).deleteDeletedByIdIn(List.of(1L));
    }

    @Test
    void cleanUp_abandonedUploads_areMarkedDeletedThenPurged() {
        Instant startedAt = Instant.now();
        when(listingImageRepository.findDeleted(any(Pageable.class))).thenReturn(List.of(FIRST));

        service.cleanUp();

        ArgumentCaptor<Instant> createdBefore = ArgumentCaptor.forClass(Instant.class);
        verify(listingImageRepository).markDeleted(
                eq(EnumSet.of(ListingImageStatus.PENDING, ListingImageStatus.UPLOADED)), createdBefore.capture());
        assertThat(createdBefore.getValue())
                .isBetween(startedAt.minus(Duration.ofHours(24)), Instant.now().minus(Duration.ofHours(24)));
        verify(objectStorage).delete(FIRST.objectKey());
        verify(listingImageRepository).deleteDeletedByIdIn(List.of(1L));
    }

    @Test
    void cleanUp_nothingDeleted_touchesNoStorage() {
        when(listingImageRepository.findDeleted(any(Pageable.class))).thenReturn(List.of());

        service.cleanUp();

        verify(objectStorage, never()).delete(any());
        verify(listingImageRepository, never()).deleteDeletedByIdIn(any());
    }
}
