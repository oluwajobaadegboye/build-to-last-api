package com.btl.transport.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one look every email shares: a clean white card with the program's name at the top, a clear
 * title, short paragraphs, an optional code box and details table, one button for the main action
 * (with the link written out small underneath in case the button doesn't work) and a signature.
 *
 * <p>Built with tables and inline styles so it renders the same in Gmail, Outlook and Apple Mail.
 * All text passed in is escaped here; only {@link #html()} produces markup.
 */
public final class EmailLayout {

    // Same palette as the web app: near-black on warm white.
    private static final String BG = "#F5F5F2";
    private static final String CARD = "#FFFFFF";
    private static final String BORDER = "#EAEAE5";
    private static final String INK = "#0A0A0A";
    private static final String BODY = "#3F3F3B";
    private static final String MUTED = "#6B6B66";
    private static final String FAINT = "#9B9B95";
    private static final String FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String MONO = "'SFMono-Regular',Menlo,Consolas,'Courier New',monospace";

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+");

    private final String eyebrow;      // program name, or "ProgramHub"
    private final String place;        // "Camp Hall · Denver, CO" or ""
    private final String signoff;      // "Pinewood Youth Camp Team"
    private String title = "";
    private String preheader = "";
    private String footerNote = "";
    private final List<String> blocks = new ArrayList<>();

    private EmailLayout(String eyebrow, String place, String signoff) {
        this.eyebrow = eyebrow;
        this.place = place == null ? "" : place;
        this.signoff = signoff;
    }

    /** An email sent on behalf of a program (its name in the header and signature). */
    public static EmailLayout forProgram(ProgramBranding brand) {
        return new EmailLayout(brand.headerName(), brand.place(), brand.teamName());
    }

    /** An email from the platform itself (account emails, not tied to one program). */
    public static EmailLayout forPlatform() {
        return new EmailLayout("ProgramHub", "", "The ProgramHub Team");
    }

    public EmailLayout title(String t) { this.title = t == null ? "" : t; return this; }

    /** The grey preview line inboxes show next to the subject. */
    public EmailLayout preheader(String p) { this.preheader = p == null ? "" : p; return this; }

    /** Small print under the card, e.g. why they got this email. */
    public EmailLayout footerNote(String n) { this.footerNote = n == null ? "" : n; return this; }

    public EmailLayout greeting(String name) {
        return paragraph(name == null || name.isBlank() ? "Hello," : "Hi " + name.trim() + ",");
    }

    public EmailLayout paragraph(String text) {
        if (text == null || text.isBlank()) return this;
        blocks.add("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:" + BODY + ";\">"
            + ProgramBranding.escape(text).replace("\n", "<br>") + "</p>");
        return this;
    }

    /**
     * Free text (e.g. a message the program wrote itself): blank lines start new paragraphs, and any
     * web address in it becomes a short link instead of a long raw URL.
     */
    public EmailLayout text(String text, String linkLabel) {
        if (text == null || text.isBlank()) return this;
        for (String para : text.trim().split("\\n\\s*\\n")) {
            Matcher m = URL.matcher(para);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(ProgramBranding.escape(para.substring(last, m.start())));
                sb.append(link(m.group(), linkLabel == null ? "this link" : linkLabel));
                last = m.end();
            }
            sb.append(ProgramBranding.escape(para.substring(last)));
            blocks.add("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:" + BODY + ";\">"
                + sb.toString().replace("\n", "<br>") + "</p>");
        }
        return this;
    }

    /** A highlighted code (registration code, etc.) in its own box. */
    public EmailLayout code(String label, String code) {
        if (code == null || code.isBlank()) return this;
        blocks.add("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 20px;\">"
            + "<tr><td align=\"center\" style=\"background:" + BG + ";border:1px solid " + BORDER + ";border-radius:10px;padding:20px 16px;\">"
            + "<div style=\"font-size:11px;font-weight:600;letter-spacing:0.12em;text-transform:uppercase;color:" + MUTED + ";\">"
            + ProgramBranding.escape(label) + "</div>"
            + "<div style=\"margin-top:8px;font-family:" + MONO + ";font-size:28px;font-weight:700;letter-spacing:0.14em;color:" + INK + ";\">"
            + ProgramBranding.escape(code) + "</div>"
            + "</td></tr></table>");
        return this;
    }

    /** Label / value rows (skips rows with no value). */
    public EmailLayout details(String heading, List<String[]> rows) {
        List<String[]> kept = new ArrayList<>();
        for (String[] r : rows) if (r != null && r.length == 2 && r[1] != null && !r[1].isBlank()) kept.add(r);
        if (kept.isEmpty()) return this;
        StringBuilder sb = new StringBuilder();
        if (heading != null && !heading.isBlank()) {
            sb.append("<div style=\"margin:8px 0 8px;font-size:11px;font-weight:600;letter-spacing:0.12em;text-transform:uppercase;color:")
              .append(MUTED).append(";\">").append(ProgramBranding.escape(heading)).append("</div>");
        }
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;border:1px solid ")
          .append(BORDER).append(";border-radius:10px;border-collapse:separate;\">");
        for (int i = 0; i < kept.size(); i++) {
            String top = i == 0 ? "" : "border-top:1px solid " + BORDER + ";";
            sb.append("<tr>")
              .append("<td style=\"").append(top).append("padding:11px 14px;font-size:13px;color:").append(MUTED).append(";width:38%;vertical-align:top;\">")
              .append(ProgramBranding.escape(kept.get(i)[0])).append("</td>")
              .append("<td style=\"").append(top).append("padding:11px 14px;font-size:14px;font-weight:600;color:").append(INK).append(";vertical-align:top;word-break:break-word;\">")
              .append(ProgramBranding.escape(kept.get(i)[1])).append("</td>")
              .append("</tr>");
        }
        blocks.add(sb.append("</table>").toString());
        return this;
    }

    /** The main action. The full link is also written out small, for when the button won't open. */
    public EmailLayout button(String label, String url) {
        if (url == null || url.isBlank()) return this;
        String href = ProgramBranding.escape(url);
        blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 12px;\"><tr>"
            + "<td style=\"background:" + INK + ";border-radius:8px;\">"
            + "<a href=\"" + href + "\" target=\"_blank\" style=\"display:inline-block;padding:13px 24px;font-family:" + FONT
            + ";font-size:15px;font-weight:600;color:#FFFFFF;text-decoration:none;border-radius:8px;\">"
            + ProgramBranding.escape(label) + "</a></td></tr></table>"
            + "<p style=\"margin:0 0 20px;font-size:12px;line-height:1.5;color:" + FAINT + ";\">"
            + "Button not working? Copy this link into your browser:<br>"
            + "<a href=\"" + href + "\" target=\"_blank\" style=\"color:" + MUTED + ";text-decoration:underline;word-break:break-all;\">"
            + href + "</a></p>");
        return this;
    }

    /** A short list of points (tips, next steps). */
    public EmailLayout bullets(String heading, List<String> items) {
        if (items == null || items.isEmpty()) return this;
        StringBuilder sb = new StringBuilder("<div style=\"margin:4px 0 20px;padding:14px 16px;background:" + BG + ";border-radius:10px;\">");
        if (heading != null && !heading.isBlank()) {
            sb.append("<div style=\"margin:0 0 6px;font-size:13px;font-weight:600;color:").append(INK).append(";\">")
              .append(ProgramBranding.escape(heading)).append("</div>");
        }
        for (String item : items) {
            sb.append("<div style=\"margin:4px 0;font-size:13px;line-height:1.55;color:").append(BODY).append(";\">&bull;&nbsp; ")
              .append(ProgramBranding.escape(item)).append("</div>");
        }
        blocks.add(sb.append("</div>").toString());
        return this;
    }

    /** Smaller, quieter text (e.g. "If you didn't sign up, ignore this email"). */
    public EmailLayout note(String text) {
        if (text == null || text.isBlank()) return this;
        blocks.add("<p style=\"margin:0 0 16px;font-size:13px;line-height:1.55;color:" + MUTED + ";\">"
            + ProgramBranding.escape(text) + "</p>");
        return this;
    }

    public String html() {
        String e = ProgramBranding.escape(eyebrow);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">")
          .append("<title>").append(ProgramBranding.escape(title)).append("</title></head>")
          .append("<body style=\"margin:0;padding:0;background:").append(BG).append(";font-family:").append(FONT).append(";\">");
        if (!preheader.isBlank()) {
            sb.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
              .append(ProgramBranding.escape(preheader)).append("</div>");
        }
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:").append(BG).append(";\">")
          .append("<tr><td align=\"center\" style=\"padding:32px 16px;\">")
          .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;\">")
          // Header: program name (and where it is)
          .append("<tr><td style=\"padding:0 4px 14px;\">")
          .append("<div style=\"font-size:15px;font-weight:700;color:").append(INK).append(";\">").append(e).append("</div>");
        if (!place.isBlank()) {
            sb.append("<div style=\"margin-top:2px;font-size:12px;color:").append(MUTED).append(";\">").append(ProgramBranding.escape(place)).append("</div>");
        }
        sb.append("</td></tr>")
          // Card
          .append("<tr><td style=\"background:").append(CARD).append(";border:1px solid ").append(BORDER)
          .append(";border-radius:12px;padding:32px 32px 16px;\">");
        if (!title.isBlank()) {
            sb.append("<h1 style=\"margin:0 0 20px;font-size:22px;line-height:1.3;font-weight:700;color:").append(INK).append(";\">")
              .append(ProgramBranding.escape(title)).append("</h1>");
        }
        for (String b : blocks) sb.append(b);
        sb.append("<p style=\"margin:8px 0 16px;font-size:15px;line-height:1.6;color:").append(BODY).append(";\">Thanks,<br>")
          .append(ProgramBranding.escape(signoff)).append("</p>")
          .append("</td></tr>")
          // Footer
          .append("<tr><td style=\"padding:16px 4px 0;font-size:12px;line-height:1.5;color:").append(FAINT).append(";text-align:center;\">");
        if (!footerNote.isBlank()) sb.append(ProgramBranding.escape(footerNote)).append("<br>");
        sb.append("Sent with ProgramHub")
          .append("</td></tr></table></td></tr></table></body></html>");
        return sb.toString();
    }

    private static String link(String url, String label) {
        String href = ProgramBranding.escape(url);
        return "<a href=\"" + href + "\" target=\"_blank\" style=\"color:" + INK + ";font-weight:600;text-decoration:underline;\">"
            + ProgramBranding.escape(label) + "</a>";
    }

    /** A details row. */
    public static String[] row(String label, String value) { return new String[]{label, value}; }
}
