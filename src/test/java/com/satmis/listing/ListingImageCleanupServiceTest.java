package com.satmis.listing;

import com.satmis.enums.ListingImageStatus;
import com.satmis.storage.ObjectStorage;
import com.satmis.storage.StorageException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingImageCleanupServiceTest {

    private static final DeletedImageView FIRST = new DeletedImageView(1L, "listings/a.jpg");
    private static final DeletedImageView SECOND = new DeletedImageView(2L, "listings/b.jpg");
    private static final DeletedImageView THIRD = new DeletedImageView(3L, "listings/c.jpg");

    @Mock
    private ListingImageRepository listingImageRepository;
    @Mock
    private ObjectStorage objectStorage;

    private ListingImageCleanupService service;

    @BeforeEach
    void setUp() {
        ListingImageProperties properties = new ListingImageProperties(DataSize.ofMegabytes(10), 40,
                Duration.ofMinutes(10), Duration.ofHours(24));
        service = new ListingImageCleanupService(listingImageRepository, objectStorage, properties);
    }

    @Test
    void purge_allFilesRemoved_deletesAllRows() {
        int removed = service.purge(List.of(FIRST, SECOND));

        assertThat(removed).isEqualTo(2);
        verify(objectStorage).delete(FIRST.objectKey());
        verify(objectStorage).delete(SECOND.objectKey());
        verify(listingImageRepository).deleteDeletedByIdIn(List.of(1L, 2L));
    }

    @Test
    void purge_storageFailsMidway_deletesOnlyRowsWhoseFileIsGone() {
        // Lenient: the first file is deleted with a different key than the stubbed one
        lenient().doThrow(new StorageException("Deleting failed", null)).when(objectStorage).delete(SECOND.objectKey());

        int removed = service.purge(List.of(FIRST, SECOND, THIRD));

        assertThat(removed).isEqualTo(1);
        verify(objectStorage, never()).delete(THIRD.objectKey());
        verify(listingImageRepository).deleteDeletedByIdIn(List.of(1L));
    }

    @Test
    void purge_storageDown_keepsAllRows() {
        doThrow(new StorageException("Deleting failed", null)).when(objectStorage).delete(any());

        assertThat(service.purge(List.of(FIRST))).isZero();
        verify(listingImageRepository, never()).deleteDeletedByIdIn(any());
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
