package com.satmis.comment;

import com.satmis.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorNamesTest {

    @Test
    void mask_fullName_keepsFirstNameAndLastNameInitial() {
        assertThat(AuthorNames.mask(user("Ayşe", "Yılmaz"))).isEqualTo("Ayşe Y.");
    }

    @Test
    void mask_turkishInitialAndSurroundingSpaces_keepsTheLetterAsWritten() {
        assertThat(AuthorNames.mask(user("  Feridun ", " İnce"))).isEqualTo("Feridun İ.");
        assertThat(AuthorNames.mask(user("Can", "ılgaz"))).isEqualTo("Can ı.");
    }

    @Test
    void mask_blankLastName_returnsFirstNameOnly() {
        assertThat(AuthorNames.mask(user("Ali", "  "))).isEqualTo("Ali");
    }

    private static User user(String firstName, String lastName) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }
}
