package com.satmis.storage;

// A row marked DELETED whose stored file is still to be removed
public record DeletedFile(Long id, String objectKey) {
}
