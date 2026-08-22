-- Campus contact strip: university email + phone (support_email already exists).

ALTER TABLE institution_branding
    ADD COLUMN contact_email VARCHAR(254),
    ADD COLUMN phone_number VARCHAR(40);
