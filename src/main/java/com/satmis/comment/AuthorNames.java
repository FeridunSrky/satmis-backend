package com.satmis.comment;

import com.satmis.user.User;

// Comments show "Ayşe Y.": enough to tell authors apart without publishing full names
final class AuthorNames {

    private AuthorNames() {
    }

    static String mask(User author) {
        String firstName = author.getFirstName().strip();
        String lastName = author.getLastName().strip();
        if (lastName.isEmpty()) {
            return firstName;
        }
        // Code point, not char, so a surrogate pair is never cut in half
        String initial = new String(Character.toChars(lastName.codePointAt(0)));
        return firstName + " " + initial + ".";
    }
}
