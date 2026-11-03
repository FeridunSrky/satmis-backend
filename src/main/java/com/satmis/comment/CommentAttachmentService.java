package com.satmis.comment;

import com.satmis.comment.dto.AttachmentResponse;
import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.enums.AttachmentType;
import com.satmis.enums.CommentAttachmentStatus;
import com.satmis.listing.dto.ImageUploadRequest;
import com.satmis.listing.dto.ImageUploadResponse;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Same flow as listing images: requestUpload issues a presigned URL, the browser PUTs the file, completeUpload checks it
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(CommentAttachmentProperties.class)
public class CommentAttachmentService {

    private static final Set<CommentAttachmentStatus> UNATTACHED =
            EnumSet.of(CommentAttachmentStatus.PENDING, CommentAttachmentStatus.UPLOADED);
    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final CommentAttachmentRepository attachmentRepository;
    private final CommentAuthorPolicy commentAuthorPolicy;
    private final ObjectStorage objectStorage;
    private final CommentAttachmentProperties properties;
    private final CommentMapper commentMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ImageUploadResponse requestUpload(Long userId, ImageUploadRequest request) {
        AttachmentType fileType = AttachmentType.fromMimeType(request.contentType())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Sadece JPEG, PNG, WEBP görseller ve PDF belgeler yüklenebilir"));
        if (request.sizeBytes() > properties.maxSize().toBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Dosya en fazla " + properties.maxSize().toMegabytes() + " MB olabilir");
        }
        User owner = commentAuthorPolicy.requireAllowedToComment(userId);
        // Soft limit: simultaneous requests may pass the count together, which only adds a few files
        if (attachmentRepository.countByOwnerIdAndStatusIn(userId, UNATTACHED) >= properties.maxUnattachedPerUser()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ATTACHMENT_LIMIT_REACHED,
                    "Yoruma eklenmemiş dosya sınırına ulaştınız; yorumunuzu gönderin ya da kullanmadığınız dosyaları silin");
        }

        CommentAttachment attachment = new CommentAttachment();
        attachment.setOwner(owner);
        attachment.setStatus(CommentAttachmentStatus.PENDING);
        attachment.setFileType(fileType);
        attachment.setSizeBytes(request.sizeBytes());
        attachment.setObjectKey(newObjectKey(fileType));
        attachmentRepository.save(attachment);

        PresignedUpload upload = objectStorage.presignUpload(attachment.getObjectKey(), fileType.mimeType(),
                request.sizeBytes(), properties.uploadUrlTtl(), fileType.isDownloadOnly());
        return new ImageUploadResponse(attachment.getId(), upload.url(), upload.headers(), upload.expiresAt());
    }

    // Idempotent: a file that was already checked is returned as is
    @Transactional(noRollbackFor = ApiException.class)
    public AttachmentResponse completeUpload(Long userId, Long attachmentId) {
        CommentAttachment attachment = attachmentRepository.findByIdAndOwnerId(attachmentId, userId)
                .filter(found -> found.getStatus() != CommentAttachmentStatus.DELETED)
                .orElseThrow(CommentAttachmentService::attachmentNotFound);
        if (attachment.getStatus() == CommentAttachmentStatus.PENDING) {
            verifyStoredFile(attachment);
            attachment.setStatus(CommentAttachmentStatus.UPLOADED);
        }
        return commentMapper.toAttachment(attachment, objectStorage);
    }

    // Repeating the call is harmless: 204 while the file is still DELETED, 404 once the cleanup removed it
    @Transactional
    public void deleteUpload(Long userId, Long attachmentId) {
        CommentAttachment attachment = attachmentRepository.findByIdAndOwnerId(attachmentId, userId)
                .orElseThrow(CommentAttachmentService::attachmentNotFound);
        if (attachment.getStatus() == CommentAttachmentStatus.ATTACHED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yoruma eklenmiş dosya ayrıca silinemez; yorumu silin");
        }
        if (attachment.getStatus() != CommentAttachmentStatus.DELETED) {
            markDeleted(List.of(attachment));
        }
    }

    /**
     * Attaches the author's checked uploads to a new comment, in this order. Comments are never edited, so each
     * attachment must be UPLOADED; anything else (another user's, already used, unchecked) is refused.
     */
    @Transactional
    public void attach(ListingComment comment, List<Long> attachmentIds) {
        if (attachmentIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dosya listesi boş değer içeremez");
        }
        if (attachmentIds.stream().distinct().count() != attachmentIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aynı dosya bir yoruma iki kez eklenemez");
        }
        if (attachmentIds.isEmpty()) {
            return;
        }
        Map<Long, CommentAttachment> ownedById = attachmentRepository
                .findAllByIdInAndOwnerId(attachmentIds, comment.getAuthor().getId()).stream()
                .collect(Collectors.toMap(CommentAttachment::getId, Function.identity()));
        for (int index = 0; index < attachmentIds.size(); index++) {
            CommentAttachment attachment = requireUploaded(ownedById.get(attachmentIds.get(index)));
            attachment.setComment(comment);
            attachment.setStatus(CommentAttachmentStatus.ATTACHED);
            attachment.setDisplayOrder(index);
            comment.getAttachments().add(attachment);
        }
    }

    // Called before comments are deleted; the rows stay so their stored files can be removed
    @Transactional
    public void releaseForComments(Collection<Long> commentIds) {
        markDeleted(attachmentRepository.findByCommentIdIn(commentIds));
    }

    // Called before a listing is deleted: its comments go with it at the database level
    @Transactional
    public void releaseForListing(Long listingId) {
        markDeleted(attachmentRepository.findByListingId(listingId));
    }

    private static CommentAttachment requireUploaded(CommentAttachment attachment) {
        if (attachment == null || attachment.getStatus() != CommentAttachmentStatus.UPLOADED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ATTACHMENT_NOT_AVAILABLE,
                    "Dosyalardan biri kullanılamıyor: yüklemesi tamamlanmamış, silinmiş ya da başka bir yorumda kullanılmış");
        }
        return attachment;
    }

    // The stored files are removed after commit by CommentAttachmentDeletionListener, or later by the cleanup job
    private void markDeleted(List<CommentAttachment> attachments) {
        if (attachments.isEmpty()) {
            return;
        }
        for (CommentAttachment attachment : attachments) {
            attachment.setStatus(CommentAttachmentStatus.DELETED);
            attachment.setComment(null);
            attachment.setDisplayOrder(null);
        }
        eventPublisher.publishEvent(new CommentAttachmentsDeletedEvent(attachments.stream()
                .map(attachment -> new DeletedAttachmentView(attachment.getId(), attachment.getObjectKey()))
                .toList()));
    }

    // Size and type are signed into the upload URL, but only the file's own bytes prove its type
    private void verifyStoredFile(CommentAttachment attachment) {
        String key = attachment.getObjectKey();
        StoredObject stored = objectStorage.find(key)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ATTACHMENT_NOT_UPLOADED,
                        "Dosya henüz yüklenmemiş"));
        boolean sameSize = stored.sizeBytes() == attachment.getSizeBytes();
        boolean sameType = AttachmentTypeDetector
                .detect(objectStorage.readFirstBytes(key, AttachmentTypeDetector.HEADER_LENGTH))
                .filter(detected -> detected == attachment.getFileType())
                .isPresent();
        if (!sameSize || !sameType) {
            log.warn("Rejected upload of comment attachment {}: size matches={}, type matches={}",
                    attachment.getId(), sameSize, sameType);
            markDeleted(List.of(attachment));
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ATTACHMENT_INVALID,
                    "Yüklenen dosya geçerli bir " + attachment.getFileType() + " dosyası değil");
        }
    }

    // Random, unguessable names; nothing in the key points back to the user
    private static String newObjectKey(AttachmentType fileType) {
        return "comments/" + KEY_MONTH.format(Instant.now()) + "/" + UUID.randomUUID() + "." + fileType.extension();
    }

    // Other users' files are reported as missing too, so ids cannot be probed
    private static ApiException attachmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Dosya bulunamadı");
    }
}
