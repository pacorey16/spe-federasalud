package eu.europa.ec.simpl.iam.exception;

import org.eclipse.edc.spi.EdcException;


/**
 * Raised when an error performing token parse is encountered.
 */
public class EdcTokenParseException extends EdcException {

    public EdcTokenParseException(String s) {
        super(s);
    }

    public EdcTokenParseException(Throwable e) {
        super(e);
    }

}
