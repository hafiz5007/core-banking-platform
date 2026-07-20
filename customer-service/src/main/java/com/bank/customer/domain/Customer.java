package com.bank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer (party) aggregate root, identified by a unique CIF (Customer Information File number).
 * Encapsulates the onboarding state machine: a customer starts {@code PENDING}, records a KYC
 * outcome and screening result, is assigned a risk rating, and is then either activated or blocked.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(nullable = false, unique = true, updatable = false, length = 12)
    private String cif;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 2)
    private String nationality;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "kyc_evidence_ref", length = 100)
    private String kycEvidenceRef;

    @Column(name = "screening_match", nullable = false)
    private boolean screeningMatch;

    @Column(name = "screening_case_ref", length = 100)
    private String screeningCaseRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", length = 10)
    private RiskRating riskRating;

    @Column(name = "kyc_refresh_due")
    private LocalDate kycRefreshDue;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
        // Required by JPA.
    }

    private Customer(String organizationId, String cif, String firstName, String lastName,
                     LocalDate dateOfBirth, String nationality, String email, String phone, String taxId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.cif = cif;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.email = email;
        this.phone = phone;
        this.taxId = taxId;
        this.status = CustomerStatus.PENDING;
        this.kycStatus = KycStatus.NOT_STARTED;
        this.screeningMatch = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Customer register(String organizationId, String cif, String firstName, String lastName,
                                    LocalDate dateOfBirth, String nationality, String email,
                                    String phone, String taxId) {
        return new Customer(organizationId, cif, firstName, lastName, dateOfBirth, nationality,
                email, phone, taxId);
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void recordKyc(KycStatus outcome, String evidenceRef) {
        this.kycStatus = outcome;
        this.kycEvidenceRef = evidenceRef;
        touch();
    }

    public void applyScreening(boolean match, String caseRef) {
        this.screeningMatch = match;
        this.screeningCaseRef = match ? caseRef : null;
        touch();
    }

    public void assignRiskRating(RiskRating rating) {
        this.riskRating = rating;
        this.kycRefreshDue = LocalDate.now().plusMonths(refreshMonths(rating));
        touch();
    }

    /**
     * Decide the final onboarding state: a sanctions/PEP match blocks activation; otherwise the
     * customer is activated only when KYC is verified, and stays pending if KYC referred.
     */
    public void resolveOnboarding() {
        if (screeningMatch) {
            this.status = CustomerStatus.BLOCKED;
        } else if (kycStatus == KycStatus.VERIFIED) {
            this.status = CustomerStatus.ACTIVE;
        } else {
            this.status = CustomerStatus.PENDING;
        }
        touch();
    }

    private static int refreshMonths(RiskRating rating) {
        return switch (rating) {
            case HIGH -> 12;
            case MEDIUM -> 24;
            case LOW -> 36;
        };
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCif() {
        return cif;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getNationality() {
        return nationality;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getTaxId() {
        return taxId;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public String getKycEvidenceRef() {
        return kycEvidenceRef;
    }

    public boolean isScreeningMatch() {
        return screeningMatch;
    }

    public String getScreeningCaseRef() {
        return screeningCaseRef;
    }

    public RiskRating getRiskRating() {
        return riskRating;
    }

    public LocalDate getKycRefreshDue() {
        return kycRefreshDue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
