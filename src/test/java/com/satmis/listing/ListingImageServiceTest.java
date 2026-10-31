package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.enums.ImageType;
import com.satmis.enums.ListingImageStatus;
import com.satmis.listing.dto.ImageUploadRequest;
import com.satmis.storage.ObjectStorage;
import com.satmis.storage.StorageException;
import com.satmis.storage.StoredObject;
import com.satmis.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingImageServiceTest {

    private static final long USER_ID = 7L;
    private static final long IMAGE_ID = 42L;
    private static final String KEY = "listings/2026/10/abc.jpg";
    private static final long SIZE = 1000L;
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Mock
    private ListingImageRepository listingImageRepository;
    @Mock
    private ListingOwnerPolicy listingOwnerPolicy;
    @Mock
    private ObjectStorage objectStorage;

    private ListingImageService service;

    @BeforeEach
    void setUp() {
        ListingImageProperties properties = new ListingImageProperties(DataSize.ofMegabytes(10), 2, Duration.ofMinutes(10));
        service = new ListingImageService(listingImageRepository, listingOwnerPolicy, objectStorage, properties);
    }

    @Test
    void requestUpload_unsupportedType_returns400WithoutTouchingStorage() {
        assertStatus(() -> service.requestUpload(USER_ID, new ImageUploadRequest("image/svg+xml", SIZE)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(objectStorage, listingImageRepository);
    }

    @Test
    void requestUpload_largerThanMaxSize_returns400() {
        long elevenMegabytes = DataSize.ofMegabytes(11).toBytes();
        assertStatus(() -> service.requestUpload(USER_ID, new ImageUploadRequest("image/jpeg", elevenMegabytes)),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(objectStorage, listingImageRepository);
    }

    @Test
    void requestUpload_unattachedLimitReached_returns422WithCode() {
        when(listingImageRepository.countByOwnerIdAndStatusIn(eq(USER_ID), any())).thenReturn(2L);

        assertThatThrownBy(() -> service.requestUpload(USER_ID, new ImageUploadRequest("image/jpeg", SIZE)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IMAGE_LIMIT_REACHED));
        verifyNoInteractions(objectStorage);
    }

    @Test
    void completeUpload_matchingFile_marksUploaded() {
        ListingImage image = pendingImage();
        when(listingImageRepository.findByIdAndOwnerId(IMAGE_ID, USER_ID)).thenReturn(Optional.of(image));
        when(objectStorage.find(KEY)).thenReturn(Optional.of(new StoredObject(SIZE, "image/jpeg")));
        when(objectStorage.readFirstBytes(eq(KEY), anyInt())).thenReturn(JPEG_HEADER);

        service.completeUpload(USER_ID, IMAGE_ID);

        assertThat(image.getStatus()).isEqualTo(ListingImageStatus.UPLOADED);
        verify(objectStorage, never()).delete(any());
    }

    @Test
    void completeUpload_fileOfAnotherType_deletesItAndReturns422() {
        ListingImage image = pendingImage();
        when(listingImageRepository.findByIdAndOwnerId(IMAGE_ID, USER_ID)).thenReturn(Optional.of(image));
        when(objectStorage.find(KEY)).thenReturn(Optional.of(new StoredObject(SIZE, "image/jpeg")));
        when(objectStorage.readFirstBytes(eq(KEY), anyInt())).thenReturn(PNG_HEADER);

        assertThatThrownBy(() -> service.completeUpload(USER_ID, IMAGE_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IMAGE_INVALID));
        verify(objectStorage).delete(KEY);
        assertThat(image.getStatus()).isEqualTo(ListingImageStatus.DELETED);
    }

    @Test
    void completeUpload_alreadyChecked_doesNotCallStorageAgain() {
        ListingImage image = pendingImage();
        image.setStatus(ListingImageStatus.UPLOADED);
        when(listingImageRepository.findByIdAndOwnerId(IMAGE_ID, USER_ID)).thenReturn(Optional.of(image));

        service.completeUpload(USER_ID, IMAGE_ID);

        verify(objectStorage, never()).find(any());
        verify(objectStorage, never()).readFirstBytes(any(), anyInt());
    }

    @Test
    void completeUpload_storageDown_propagatesAndKeepsImagePending() {
        ListingImage image = pendingImage();
        when(listingImageRepository.findByIdAndOwnerId(IMAGE_ID, USER_ID)).thenReturn(Optional.of(image));
        when(objectStorage.find(KEY)).thenThrow(new StorageException("Reading metadata failed", null));

        assertThatThrownBy(() -> service.completeUpload(USER_ID, IMAGE_ID)).isInstanceOf(StorageException.class);
        assertThat(image.getStatus()).isEqualTo(ListingImageStatus.PENDING);
    }

    @Test
    void deleteUpload_imageInListing_returns409AndKeepsIt() {
        ListingImage image = pendingImage();
        image.setStatus(ListingImageStatus.ATTACHED);
        when(listingImageRepository.findByIdAndOwnerId(IMAGE_ID, USER_ID)).thenReturn(Optional.of(image));

        assertStatus(() -> service.deleteUpload(USER_ID, IMAGE_ID), HttpStatus.CONFLICT);
        assertThat(image.getStatus()).isEqualTo(ListingImageStatus.ATTACHED);
    }

    @Test
    void deleteUpload_otherUsersImage_returns404() {
        when(listingImageRepository.findByIdAndOwnerId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertStatus(() -> service.deleteUpload(USER_ID, IMAGE_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void replaceListingImages_newOrder_attachesFirstAsCoverAndDeletesDroppedOnes() {
        Listing listing = listingWithId(1L);
        ListingImage kept = imageWithId(10L, ListingImageStatus.ATTACHED, listing);
        ListingImage dropped = imageWithId(11L, ListingImageStatus.ATTACHED, listing);
        ListingImage added = imageWithId(12L, ListingImageStatus.UPLOADED, null);
        listing.getImages().addAll(List.of(kept, dropped));
        when(listingImageRepository.findAllByIdInAndOwnerId(List.of(12L, 10L), USER_ID)).thenReturn(List.of(kept, added));

        service.replaceListingImages(listing, List.of(12L, 10L));

        assertThat(listing.getImages()).containsExactly(added, kept);
        assertThat(added.isCover()).isTrue();
        assertThat(added.getDisplayOrder()).isZero();
        assertThat(kept.isCover()).isFalse();
        assertThat(kept.getDisplayOrder()).isEqualTo(1);
        assertThat(dropped.getStatus()).isEqualTo(ListingImageStatus.DELETED);
        assertThat(dropped.getListing()).isNull();
    }

    @Test
    void replaceListingImages_imageOfAnotherListing_returns422AndChangesNothing() {
        Listing listing = listingWithId(1L);
        ListingImage foreign = imageWithId(10L, ListingImageStatus.ATTACHED, listingWithId(2L));
        when(listingImageRepository.findAllByIdInAndOwnerId(List.of(10L), USER_ID)).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.replaceListingImages(listing, List.of(10L)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IMAGE_NOT_AVAILABLE));
        assertThat(foreign.getListing().getId()).isEqualTo(2L);
    }

    @Test
    void releaseImages_listingImages_areMarkedDeletedAndDetached() {
        Listing listing = listingWithId(1L);
        ListingImage image = imageWithId(10L, ListingImageStatus.ATTACHED, listing);
        listing.getImages().add(image);

        service.releaseImages(listing);

        assertThat(image.getStatus()).isEqualTo(ListingImageStatus.DELETED);
        assertThat(image.getListing()).isNull();
        assertThat(listing.getImages()).isEmpty();
    }

    private static Listing listingWithId(long listingId) {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        Listing listing = new Listing();
        ReflectionTestUtils.setField(listing, "id", listingId);
        listing.setOwner(owner);
        return listing;
    }

    private static ListingImage imageWithId(long imageId, ListingImageStatus status, Listing listing) {
        ListingImage image = pendingImage();
        ReflectionTestUtils.setField(image, "id", imageId);
        image.setStatus(status);
        image.setListing(listing);
        return image;
    }

    private static ListingImage pendingImage() {
        ListingImage image = new ListingImage();
        image.setStatus(ListingImageStatus.PENDING);
        image.setObjectKey(KEY);
        image.setImageType(ImageType.JPEG);
        image.setSizeBytes(SIZE);
        return image;
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(expected));
    }
}
