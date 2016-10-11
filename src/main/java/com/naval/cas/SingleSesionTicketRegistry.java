package com.naval.cas;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.validation.constraints.NotNull;

import org.jasig.cas.logout.LogoutManager;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.ServiceTicketImpl;
import org.jasig.cas.ticket.Ticket;
import org.jasig.cas.ticket.TicketGrantingTicket;
import org.jasig.cas.ticket.TicketGrantingTicketImpl;
import org.jasig.cas.ticket.registry.AbstractTicketRegistry;
import org.springframework.util.Assert;

import com.google.common.util.concurrent.Service;

/**
 * 
 * 
 * 
 * @author Naval isSingleSesionPerUser : Only allow single session or not :
 *         Values : true/false. logoutManager-ref : Reference to logoutManager
 *         to destroy previous session of same user.
 *
 *
 */
public class SingleSesionTicketRegistry extends AbstractTicketRegistry {

    @NotNull
    private Boolean isSingleSesionPerUser = true;

    @NotNull
    private LogoutManager logoutManager;

    /** A HashMap to contain the tickets. */
    private final Map<String, Ticket> cache;
    private final Map<String, Ticket> userNameTicketMap;

    public SingleSesionTicketRegistry() {
        this.cache = new ConcurrentHashMap<String, Ticket>();
        this.userNameTicketMap = new ConcurrentHashMap<String, Ticket>();
    }

    /**
     * Creates a new, empty registry with the specified initial capacity, load
     * factor, and concurrency level.
     *
     * @param initialCapacity
     *            - the initial capacity. The implementation performs internal
     *            sizing to accommodate this many elements.
     * @param loadFactor
     *            - the load factor threshold, used to control resizing.
     *            Resizing may be performed when the average number of elements
     *            per bin exceeds this threshold.
     * @param concurrencyLevel
     *            - the estimated number of concurrently updating threads. The
     *            implementation performs internal sizing to try to accommodate
     *            this many threads.
     */
    public SingleSesionTicketRegistry(final int initialCapacity, final float loadFactor, final int concurrencyLevel) {
        this.cache = new ConcurrentHashMap<String, Ticket>(initialCapacity, loadFactor, concurrencyLevel);
        this.userNameTicketMap = new ConcurrentHashMap<String, Ticket>(initialCapacity, loadFactor, concurrencyLevel);
    }

    /**
     * {@inheritDoc} Creates a Ticket-Granting-Ticket on authentication request
     * from the login form. If the isSingleSesionPerUser parameter is set to
     * true then the method will first check whether the user is logged in from
     * other system or not, if found true then it will destroy the previous
     * session by calling logoutManager.performLogout() on the previous ticket
     * and let user login in new browser.
     * 
     * User-name to Ticket mapping is stored in userNameTicketMap to avoid
     * unnecessary iteration on every login request
     * 
     * @throws IllegalArgumentException
     *             if the Ticket is null.
     */
    @Override
    public void addTicket(final Ticket ticket) {
        Assert.notNull(ticket, "ticket cannot be null");
        String auth = null;

        logger.debug("isSingleSesionPerUser:{}", isSingleSesionPerUser);

        if (isSingleSesionPerUser) {
            if (ticket instanceof TicketGrantingTicketImpl) {
                TicketGrantingTicketImpl impl = (TicketGrantingTicketImpl) ticket;

                auth = impl.getAuthentication().getPrincipal().toString();

                // auth = ((TicketGrantingTicket)
                // ticket).getAuthentication().getPrincipal().toString();

                TicketGrantingTicketImpl ticketFromMap = (TicketGrantingTicketImpl) userNameTicketMap.get(auth);

                if (ticketFromMap != null) {
                    // TicketGrantingTicket previousTicket =
                    // ticketFromMap.getGrantingTicket();
                    logger.debug("Previous ticket found for {}. Logging out {}", auth, ticketFromMap.getId());

                    this.cache.remove(ticketFromMap.getId());
                    userNameTicketMap.remove(auth);
                    logoutManager.performLogout(ticketFromMap);
                } else {
                    logger.debug("No previous ticket for {}", auth);
                }

                this.userNameTicketMap.put(auth, ticket);
                logger.debug("Added ticket [{}] to registry under {}....", ticket.getId(), auth);
            }
        }

        this.cache.put(ticket.getId(), ticket);
    }

    public Ticket getTicket(final String ticketId) {
        if (ticketId == null) {
            return null;
        }

        logger.debug("Attempting to retrieve ticket [{}]", ticketId);
        final Ticket ticket = this.cache.get(ticketId);

        if (ticket != null) {
            logger.debug("Ticket [{}] found in registry.", ticketId);
        }

        return ticket;
    }

    public boolean deleteTicket(final String ticketId) {
        if (ticketId == null) {
            return false;
        }
        logger.debug("Removing ticket [{}] from registry", ticketId);
        return (this.cache.remove(ticketId) != null);
    }

    public Collection<Ticket> getTickets() {
        return Collections.unmodifiableCollection(this.cache.values());
    }

    public int sessionCount() {
        int count = 0;
        for (Ticket t : this.cache.values()) {
            if (t instanceof TicketGrantingTicket) {
                count++;
            }
        }
        return count;
    }

    public int serviceTicketCount() {
        int count = 0;
        for (Ticket t : this.cache.values()) {
            if (t instanceof ServiceTicket) {
                count++;
            }
        }
        return count;
    }

    public void setLogoutManager(final LogoutManager logoutManager) {
        this.logoutManager = logoutManager;
    }

    public void setIsSingleSesionPerUser(Boolean isSingleSesionPerUser) {
        this.isSingleSesionPerUser = isSingleSesionPerUser;
    }
}
