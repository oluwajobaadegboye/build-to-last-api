package com.btl.transport.program;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "programs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ini")
    private String ini;

    @Column(name = "type")
    private String type;

    @Column(name = "start_date")
    private String startDate;

    @Column(name = "end_date")
    private String endDate;

    @Column(name = "phase")
    private String phase;

    @Column(name = "venue")
    private String venue;

    @Column(name = "venue_addr")
    private String venueAddr;

    @Column(name = "airport")
    private String airport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hotels", columnDefinition = "jsonb")
    private String hotels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "morning_runs", columnDefinition = "jsonb")
    private String morningRuns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evening_runs", columnDefinition = "jsonb")
    private String eveningRuns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "daily_schedules", columnDefinition = "jsonb")
    private String dailySchedules;

    @Column(name = "rule_window")
    private String ruleWindow;

    @Column(name = "rule_cap")
    private String ruleCap;

    @Column(name = "rule_buffer")
    private String ruleBuffer;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "logo_url")
    private String logoUrl;

    /** Optional banner/poster image (hero of the landing and registration pages). */
    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "hotel_selection_enabled")
    private Boolean hotelSelectionEnabled;

    @Column(name = "registration_open")
    private Boolean registrationOpen;

    @Column(name = "reg_title", length = 500)
    private String regTitle;

    @Column(name = "reg_description", columnDefinition = "text")
    private String regDescription;

    /** Colour set for the registration page and program header (see PAGE_THEMES); null = picked from the name. */
    @Column(name = "page_theme", length = 20)
    private String pageTheme;

    /** Registration landing design (see LANDING_STYLES); null = the default ("centered"). */
    @Column(name = "landing_style", length = 20)
    private String landingStyle;

    /** Heading of the registration step that holds the custom questions; null = "Additional Info". */
    @Column(name = "questions_title", length = 60)
    private String questionsTitle;

    /** Where souvenir size questions are asked: null = first step, "own" = their own step, else a section id. */
    @Column(name = "souvenir_step", length = 20)
    private String souvenirStep;

    /** Rooms and hostels can have chaperones (shown in attendees' help and as "My Group" for the chaperone). */
    @Column(name = "chaperones_enabled")
    private Boolean chaperonesEnabled;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "roommate_visible")
    private Boolean roommateVisible;

    @Column(name = "show_upload_csv")
    private Boolean showUploadCsv;

    @Column(name = "show_download_template")
    private Boolean showDownloadTemplate;

    @Column(name = "show_fix_unlinked")
    private Boolean showFixUnlinked;

    @Column(name = "show_notify_participants")
    private Boolean showNotifyParticipants;

    @Column(name = "show_export_recipients")
    private Boolean showExportRecipients;

    /** Daily shuttle runs between lodging and venue. Off for e.g. camps within walking distance. */
    @Column(name = "daily_shuttle_enabled", nullable = false)
    @Builder.Default
    private Boolean dailyShuttleEnabled = true;

    /** Airport arrival/departure pickups (and collecting flight details at registration). */
    @Column(name = "airport_pickups_enabled", nullable = false)
    @Builder.Default
    private Boolean airportPickupsEnabled = true;

    /** Where attendees stay: one of {@link #LODGING_TYPES}; drives labels such as "Hotel" vs "Cabin". */
    @Column(name = "lodging_type", nullable = false, length = 20)
    @Builder.Default
    private String lodgingType = "hotel";

    /**
     * "Leave from the venue" rule for departure pickups: when on, attendees whose flight leaves before
     * {@link #venueDepartureCutoff} are picked up at the venue instead of their lodging. Off by default.
     */
    @Column(name = "venue_departure_rule", nullable = false)
    @Builder.Default
    private Boolean venueDepartureRule = false;

    /** "HH:mm"; null → 10:30. Only used when {@link #venueDepartureRule} is on. */
    @Column(name = "venue_departure_cutoff", length = 5)
    private String venueDepartureCutoff;

    /** Optional registration fee; when set, payment status follows the total paid. */
    @Column(name = "registration_fee")
    private java.math.BigDecimal registrationFee;

    @Column(name = "fee_currency", nullable = false, length = 3)
    @Builder.Default
    private String feeCurrency = "USD";

    /** Show the registration fee on the public registration page (null = off). */
    @Column(name = "show_fee_on_landing")
    private Boolean showFeeOnLanding;

    /** Label for the fee on the registration page; null = "Registration fee". */
    @Column(name = "fee_label", length = 40)
    private String feeLabel;

    /** When full, new sign-ups join a waitlist instead of being turned away (null = off). */
    @Column(name = "waitlist_enabled")
    private Boolean waitlistEnabled;

    /** Payment reminder emails (manual button and automatic); receipts use paymentEmailsEnabled. Null = off. */
    @Column(name = "payment_reminders_enabled")
    private Boolean paymentRemindersEnabled;

    /** Send one automatic payment reminder this many days before the start date (null = never). */
    @Column(name = "auto_reminder_days")
    private Integer autoReminderDays;

    @Column(name = "auto_reminder_sent_at")
    private OffsetDateTime autoReminderSentAt;

    /** Attendees may correct their own name, phone and answers from their status page (null = off). */
    @Column(name = "attendee_self_edit")
    private Boolean attendeeSelfEdit;

    /** How to pay (e.g. Zelle details), shown after registering, in the email and on the attendee's page. */
    @Column(name = "payment_instructions", columnDefinition = "TEXT")
    private String paymentInstructions;

    /** Fee by answer: the question whose options set the fee (null = everyone pays registrationFee). */
    @Column(name = "fee_question_key", length = 100)
    private String feeQuestionKey;

    /** JSON {"option": amount} for feeQuestionKey; options without an amount pay registrationFee. */
    @Column(name = "fee_by_option", columnDefinition = "TEXT")
    private String feeByOption;

    /** When attendees see their room: null/always, checkin, date, checkin_or_date. */
    @Column(name = "room_visibility", length = 20)
    private String roomVisibility;

    /** For date / checkin_or_date: "YYYY-MM-DDTHH:mm" in the program's timezone. */
    @Column(name = "room_visible_from", length = 16)
    private String roomVisibleFrom;

    /** Google Sheets backup: the spreadsheet id (null = not connected), tab, its title, last sync and last error. */
    @Column(name = "sheet_id", length = 200)
    private String sheetId;

    @Column(name = "sheet_tab", length = 100)
    private String sheetTab;

    @Column(name = "sheet_title", length = 300)
    private String sheetTitle;

    @Column(name = "sheet_synced_at")
    private OffsetDateTime sheetSyncedAt;

    @Column(name = "sheet_error", columnDefinition = "TEXT")
    private String sheetError;

    /** Attendees see their payment status/balance on their status page. */
    @Column(name = "show_payment_to_attendees", nullable = false)
    @Builder.Default
    private Boolean showPaymentToAttendees = false;

    /** Registrations-only admins see amounts (not just paid / part paid / unpaid). */
    @Column(name = "registrations_see_amounts", nullable = false)
    @Builder.Default
    private Boolean registrationsSeeAmounts = true;

    /** Email attendees a receipt when a payment is recorded, and allow payment reminders. */
    @Column(name = "payment_emails_enabled", nullable = false)
    @Builder.Default
    private Boolean paymentEmailsEnabled = false;

    /** Local "YYYY-MM-DDTHH:mm" (program timezone) after which registration closes; null = no date. */
    @Column(name = "registration_closes_at", length = 16)
    private String registrationClosesAt;

    /** Admin account that created the program (null for programs made by a platform super-admin). */
    @Column(name = "created_by_admin_id")
    private Integer createdByAdminId;

    /** Registration closes when this many people have registered; null = no limit. */
    @Column(name = "max_registrations")
    private Integer maxRegistrations;

    /** Page colour sets an admin can pick; the frontend's programTheme.ts holds the same keys. */
    public static final java.util.Set<String> PAGE_THEMES =
        java.util.Set.of("violet", "teal", "amber", "blue", "rose", "forest", "graphite", "plum");

    /** Registration landing designs an admin can pick; the frontend's landingStyles.ts holds the same keys. */
    public static final java.util.Set<String> LANDING_STYLES =
        java.util.Set.of("split", "centered", "event", "ticket", "aurora", "story", "bento", "orbit", "minimal", "card", "harbor", "tide", "showcase");

    public static final java.util.Set<String> LODGING_TYPES = java.util.Set.of("hotel", "hostel", "dorm", "cabin", "lodging");

    /** Normalised lodging type; unknown or blank values fall back to "hotel". */
    public static String normalizeLodgingType(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        return LODGING_TYPES.contains(t) ? t : "hotel";
    }

    @Column(name = "breakout_num_groups")
    @Builder.Default
    private Integer breakoutNumGroups = 7;

    /** Attendees see their breakout group on their status page. */
    @Column(name = "show_breakout_group", nullable = false)
    @Builder.Default
    private Boolean showBreakoutGroup = true;

    /** Registration asks for gender (Male / Female, required). On unless the admin turns it off. */
    @Column(name = "ask_gender", nullable = false)
    @Builder.Default
    private Boolean askGender = true;

    /** Registration asks for a WhatsApp/phone number (required). On unless the admin turns it off. */
    @Column(name = "ask_phone", nullable = false)
    @Builder.Default
    private Boolean askPhone = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
