package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.enums.ImageType;
import com.satmis.enums.ListingImageStatus;
import com.satmis.listing.dto.ImageUploadRequest;
import com.satmis.listing.dto.ImageUploadResponse;
import com.satmis.listing.dto.UploadedImageResponse;
import com.satmis.storage.ObjectStorage;
import com.satmis.storage.PresignedUpload;
import com.satmis.storage.StoredObject;
import com.satmis.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Upload flow: requestUpload issues a presigned URL, the browser PUTs the file to storage, completeUpload checks it.
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ListingImageProperties.class)
public class ListingImageService {

    private static final Set<ListingImageStatus> UNATTACHED = EnumSet.of(ListingImageStatus.PENDING, ListingImageStatus.UPLOADED);
    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final ListingImageRepository listingImageRepository;
    private final ListingOwnerPolicy listingOwnerPolicy;
    private final ObjectStorage objectStorage;
    private final ListingImageProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ImageUploadResponse requestUpload(Long userId, ImageUploadRequest request) {
        ImageType imageType = ImageType.fromMimeType(request.contentType())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Sadece JPEG, PNG ve WEBP görseller yüklenebilir"));
        if (request.sizeBytes() > properties.maxSize().toBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Görsel en fazla " + properties.maxSize().toMegabytes() + " MB olabilir");
        }
        User owner = listingOwnerPolicy.requireAllowedToList(userId);
        // Soft limit: simultaneous requests may pass the count together, which only adds a few images
        if (listingImageRepository.countByOwnerIdAndStatusIn(userId, UNATTACHED) >= properties.maxUnattachedPerUser()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IMAGE_LIMIT_REACHED,
                    "İlana eklenmemiş görsel sınırına ulaştınız; ilanınızı kaydedin ya da kullanmadığınız görselleri silin");
        }

        ListingImage image = new ListingImage();
        image.setOwner(owner);
        image.setStatus(ListingImageStatus.PENDING);
        image.setImageType(imageType);
        image.setSizeBytes(request.sizeBytes());
        image.setObjectKey(newObjectKey(imageType));
        listingImageRepository.save(image);

        PresignedUpload upload = objectStorage.presignUpload(image.getObjectKey(), imageType.mimeType(),
                request.sizeBytes(), properties.uploadUrlTtl());
        return new ImageUploadResponse(image.getId(), upload.url(), upload.headers(), upload.expiresAt());
    }

    /**
     * Idempotent: an image that was already checked is returned as is. The storage calls run inside the
     * transaction; they are short and bounded by the client timeouts (app.storage.*).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UploadedImageResponse completeUpload(Long userId, Long imageId) {
        ListingImage image = findOwnedUpload(userId, imageId);
        if (image.getStatus() == ListingImageStatus.PENDING) {
            verifyStoredFile(image);
            image.setStatus(ListingImageStatus.UPLOADED);
        }
        return new UploadedImageResponse(image.getId(), objectStorage.publicUrl(image.getObjectKey()));
    }

    // Repeating the call is harmless: 204 while the image is still DELETED, 404 once the cleanup removed it
    @Transactional
    public void deleteUpload(Long userId, Long imageId) {
        ListingImage image = listingImageRepository.findByIdAndOwnerId(imageId, userId)
                .orElseThrow(ListingImageService::imageNotFound);
        if (image.getStatus() == ListingImageStatus.ATTACHED) {
            throw new ApiException(HttpStatus.CONFLICT, "İlana eklenmiş görseli kaldırmak için ilanı güncelleyin");
        }
        if (image.getStatus() != ListingImageStatus.DELETED) {
            markDeleted(List.of(image));
        }
    }

    /**
     * Makes exactly these images, in this order, the listing's images; the first is the cover. Each must be the
     * owner's checked upload or already belong to this listing. Images the listing drops are marked DELETED.
     * The listing must already have an id.
     */
    @Transactional
    public void replaceListingImages(Listing listing, List<Long> imageIds) {
        if (imageIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Görsel listesi boş değer içeremez");
        }
        Map<Long, ListingImage> ownedById = listingImageRepository
                .findAllByIdInAndOwnerId(imageIds, listing.getOwner().getId()).stream()
                .collect(Collectors.toMap(ListingImage::getId, Function.identity()));
        List<ListingImage> requested = imageIds.stream()
                .map(imageId -> requireUsableFor(listing, ownedById.get(imageId)))
                .toList();

        markDeleted(listing.getImages().stream().filter(previous -> !requested.contains(previous)).toList());
        listing.getImages().clear();
        for (int index = 0; index < requested.size(); index++) {
            ListingImage image = requested.get(index);
            image.setListing(listing);
            image.setStatus(ListingImageStatus.ATTACHED);
            image.setDisplayOrder(index);
            image.setCover(index == 0);
            listing.getImages().add(image);
        }
    }

    // Called before a listing is deleted; the rows stay so their stored files can be removed
    @Transactional
    public void releaseImages(Listing listing) {
        markDeleted(List.copyOf(listing.getImages()));
        listing.getImages().clear();
    }

    private static ListingImage requireUsableFor(Listing listing, ListingImage image) {
        boolean usable = image != null
                && (image.getStatus() == ListingImageStatus.UPLOADED
                || (image.getStatus() == ListingImageStatus.ATTACHED && listing.equals(image.getListing())));
        if (!usable) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IMAGE_NOT_AVAILABLE,
                    "Görsellerden biri kullanılamıyor: yüklemesi tamamlanmamış, silinmiş ya da başka bir ilana ait");
        }
        return image;
    }

    // The stored files are removed after commit by ListingImageDeletionListener, or later by the cleanup job
    private void markDeleted(List<ListingImage> images) {
        if (images.isEmpty()) {
            return;
        }
        for (ListingImage image : images) {
            image.setStatus(ListingImageStatus.DELETED);
            image.setListing(null);
            image.setDisplayOrder(null);
            image.setCover(false);
        }
        eventPublisher.publishEvent(new ListingImagesDeletedEvent(images.stream()
                .map(image -> new DeletedImageView(image.getId(), image.getObjectKey()))
                .toList()));
    }

    // Size and type are signed into the upload URL, but only the file's own bytes prove it is that image type
    private void verifyStoredFile(ListingImage image) {
        String key = image.getObjectKey();
        StoredObject stored = objectStorage.find(key)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IMAGE_NOT_UPLOADED,
                        "Görsel dosyası henüz yüklenmemiş"));
        boolean sameSize = stored.sizeBytes() == image.getSizeBytes();
        boolean sameType = ImageTypeDetector.detect(objectStorage.readFirstBytes(key, ImageTypeDetector.HEADER_LENGTH))
                .filter(detected -> detected == image.getImageType())
                .isPresent();
        if (!sameSize || !sameType) {
            log.warn("Rejected upload of listing image {}: size matches={}, type matches={}", image.getId(), sameSize, sameType);
            markDeleted(List.of(image));
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IMAGE_INVALID,
                    "Yüklenen dosya geçerli bir " + image.getImageType() + " görseli değil");
        }
    }

    // Other users' images are reported as missing too, so ids cannot be probed
    private ListingImage findOwnedUpload(Long userId, Long imageId) {
        return listingImageRepository.findByIdAndOwnerId(imageId, userId)
                .filter(image -> image.getStatus() != ListingImageStatus.DELETED)
                .orElseThrow(ListingImageService::imageNotFound);
    }

    // Random, unguessable names; nothing in the key points back to the user
    private static String newObjectKey(ImageType imageType) {
        return "listings/" + KEY_MONTH.format(Instant.now()) + "/" + UUID.randomUUID() + "." + imageType.extension();
    }

    private static ApiException imageNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Görsel bulunamadı");
    }
}
