package dev.atelierflow.common.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Records the author of each revision: the authenticated user of the current request, or
 * {@value #SYSTEM} for changes made by background jobs (preventive scheduler, telemetry).
 */
public class AuditRevisionListener implements RevisionListener {

    public static final String SYSTEM = "system";

    @Override
    public void newRevision(Object revisionEntity) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        ((AuditRevision) revisionEntity).setUsername(authenticated ? authentication.getName() : SYSTEM);
    }
}
