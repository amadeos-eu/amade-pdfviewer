package eu.amadeos.apps.pdfviewer.domain;

/** The document is encrypted and needs a password, or the given password was wrong. */
public final class PasswordException extends Exception {
    private final boolean wrong;

    public PasswordException(boolean wrong) {
        super(wrong ? "Wrong password" : "Password needed");
        this.wrong = wrong;
    }

    /** True when a password was given and is wrong; false when none was given. */
    public boolean wrong() {
        return wrong;
    }
}
