package com.satmis.comment;

import com.satmis.listing.Listing;

// Comments stay readable after a sale, on the sold vehicles page; new comments need a live listing
final class CommentVisibility {

    private CommentVisibility() {
    }

    static boolean isShownOn(Listing listing) {
        return listing.isPubliclyVisible() || listing.isPubliclySold();
    }

    static boolean acceptsComments(Listing listing) {
        return listing.isPubliclyVisible();
    }
}
