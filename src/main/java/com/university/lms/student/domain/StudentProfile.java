package com.university.lms.student.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;

/**
 * Demographic and contact detail for a {@link Student}.
 *
 * <p>Split from {@code Student} because it is read far less often and is the part of the record
 * subject to data-protection handling; keeping it in its own table means the hot path never loads
 * personal data it does not need.
 *
 * <p>Campus login email stays in the identity module. A separate personal email may be stored here
 * for correspondence when it differs from the campus account.
 */
@Entity
@Table(name = "student_profiles")
@Getter
public class StudentProfile extends BaseEntity {

    @Column(name = "personal_email", length = 254)
    private String personalEmail;

    @Column(name = "gender", length = 30)
    private String gender;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    protected StudentProfile() {
        // for JPA
    }

    public static StudentProfile empty() {
        return new StudentProfile();
    }

    public void updateContact(String phoneNumber, String nationality, LocalDate dateOfBirth) {
        this.phoneNumber = phoneNumber;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
    }

    public void updateAddress(String addressLine1, String addressLine2, String city, String country) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.country = country;
    }

    public void updateEmergencyContact(String name, String phone) {
        this.emergencyContactName = name;
        this.emergencyContactPhone = phone;
    }

    public void updatePersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public void updateGender(String gender) {
        this.gender = gender;
    }
}
