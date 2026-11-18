package com.satmis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoredFilePurgerTest {

    private static final DeletedFile FIRST = new DeletedFile(1L, "listings/a.jpg");
    private static final DeletedFile SECOND = new DeletedFile(2L, "listings/b.jpg");
    private static final DeletedFile THIRD = new DeletedFile(3L, "listings/c.jpg");

    @Mock
    private ObjectStorage objectStorage;

    private StoredFilePurger purger;
    private final List<List<Long>> deletedRowBatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        purger = new StoredFilePurger(objectStorage);
    }

    @Test
    void purge_allFilesRemoved_deletesAllRows() {
        int removed = purger.purge(List.of(FIRST, SECOND), deletedRowBatches::add);

        assertThat(removed).isEqualTo(2);
        verify(objectStorage).delete(FIRST.objectKey());
        verify(objectStorage).delete(SECOND.objectKey());
        assertThat(deletedRowBatches).containsExactly(List.of(1L, 2L));
    }

    @Test
    void purge_storageFailsMidway_deletesOnlyRowsWhoseFileIsGone() {
        // Lenient: the first file is deleted with a different key than the stubbed one
        lenient().doThrow(new StorageException("Deleting failed", null)).when(objectStorage).delete(SECOND.objectKey());

        int removed = purger.purge(List.of(FIRST, SECOND, THIRD), deletedRowBatches::add);

        assertThat(removed).isEqualTo(1);
        verify(objectStorage, never()).delete(THIRD.objectKey());
        assertThat(deletedRowBatches).containsExactly(List.of(1L));
    }

    @Test
    void purge_storageDown_keepsAllRows() {
        doThrow(new StorageException("Deleting failed", null)).when(objectStorage).delete(any());

        assertThat(purger.purge(List.of(FIRST), deletedRowBatches::add)).isZero();
        assertThat(deletedRowBatches).isEmpty();
    }

    @Test
    void purgeAll_nothingDeleted_touchesNoStorage() {
        int removed = purger.purgeAll(pageable -> List.of(), deletedRowBatches::add);

        assertThat(removed).isZero();
        verify(objectStorage, never()).delete(any());
        assertThat(deletedRowBatches).isEmpty();
    }
}
