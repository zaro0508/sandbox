package org.sagebionetworks.bridge.services.email;

import java.util.Set;

import javax.mail.MessagingException;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Iterables;

public abstract class MimeTypeEmailProvider {

    private Study study;
    
    protected MimeTypeEmailProvider(Study study) {
        this.study = study;
    }
    
    /**
     * Get the sender email address without any further formatting. So for example, if the provider formats the sender
     * email as <code>"Study Name" &lt;email@email.com&gtl</code>, This method should return only
     * <code>email@email.com</code>.
     */
    public String getPlainSenderEmail() {
        Set<String> senderEmails = BridgeUtils.commaListToOrderedSet(getStudy().getSupportEmail());
        return Iterables.getFirst(senderEmails, null);
    }
    public String getFormattedSenderEmail() {
        String senderEmail = getPlainSenderEmail();
        return String.format("%s <%s>", getStudy().getName(), senderEmail);
    }
    public Study getStudy() {
        return study;
    }
    
    public abstract MimeTypeEmail getMimeTypeEmail() throws MessagingException;
    
}
