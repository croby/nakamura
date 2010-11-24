package org.sakaiproject.nakamura.api.lite.jackrabbit;

import java.security.Principal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.SessionListener;
import org.sakaiproject.nakamura.api.lite.ConnectionPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysql.jdbc.jdbc2.optional.SuspendableXAConnection;

public class SparseMapUserManager implements UserManager, SessionListener {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(SparseMapUserManager.class);
	static final String SECURITY_ROOT_PATH = "/rep:security";
	static final String AUTHORIZABLES_PATH = SECURITY_ROOT_PATH
			+ "/rep:authorizables";
	static final String USERS_PATH = AUTHORIZABLES_PATH + "/rep:users";
	static final String GROUPS_PATH = AUTHORIZABLES_PATH + "/rep:groups";
	private Session session;
	private AuthorizableManager authorizableManager;
	private ValueFactory valueFactory;
	private Repository sparseRepository;
	private AccessControlManager accessControlManager;
	private SessionImpl jcrSession;
	private AtomicInteger systemSessionCounter = new AtomicInteger();
	private AtomicInteger sessionCounter = new AtomicInteger();

	public SparseMapUserManager(SessionImpl jcrSession, String adminId,
			Properties config) throws ConnectionPoolException,
			StorageClientException, AccessDeniedException {
		sparseRepository = SparseComponentHolder.getSparseRepositoryInstance();
		session = sparseRepository.loginAdministrative(jcrSession.getUserID());
		
		authorizableManager = session.getAuthorizableManager();
		accessControlManager = session.getAccessControlManager();
		valueFactory = jcrSession.getValueFactory();
		this.jcrSession = jcrSession;
		jcrSession.addListener(this);
		int systemSessions = systemSessionCounter.get();
		int sessions = sessionCounter.get();
		if ( "org.apache.jackrabbit.core.SystemSession".equals(session.getClass().getName()) ) {
			systemSessions = systemSessionCounter.incrementAndGet();
		} else {
			sessions = sessionCounter.incrementAndGet();
		}
		LOGGER.info("Logged into sparse triggered bu Session {} {} {} ",new Object[]{jcrSession, sessions, systemSessions});

		
	}

	public Authorizable getAuthorizable(String id) throws RepositoryException {
		try {
			org.sakaiproject.nakamura.api.lite.authorizable.Authorizable auth = session
					.getAuthorizableManager().findAuthorizable(id);
			if (auth != null) {
				if (auth instanceof org.sakaiproject.nakamura.api.lite.authorizable.Group) {
					return new SparseGroup(
							(org.sakaiproject.nakamura.api.lite.authorizable.Group) auth,
							authorizableManager, accessControlManager,
							valueFactory);
				} else {
					return new SparseUser(
							(org.sakaiproject.nakamura.api.lite.authorizable.User) auth,
							authorizableManager, accessControlManager,
							valueFactory);
				}
			}
		} catch (AccessDeniedException e) {
			LOGGER.info("Getting {} denied: {}", id, e.getMessage());
			return null;
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		}
		return null;
	}

	public Authorizable getAuthorizable(Principal principal)
			throws RepositoryException {
		return getAuthorizable(principal.getName());
	}

	public Iterator<Authorizable> findAuthorizables(String propertyName,
			String value) throws RepositoryException {
		return findAuthorizables(propertyName, value, SEARCH_TYPE_AUTHORIZABLE);
	}

	public Iterator<Authorizable> findAuthorizables(String propertyName,
			String value, int searchType) throws RepositoryException {
		try {
			switch (searchType) {
			case SEARCH_TYPE_AUTHORIZABLE:
				return new SparseAuthorizableIterator(
						authorizableManager
								.findAuthorizable(
										propertyName,
										value,
										org.sakaiproject.nakamura.api.lite.authorizable.Authorizable.class),
						authorizableManager, accessControlManager, valueFactory);
			case SEARCH_TYPE_GROUP:
				return new SparseAuthorizableIterator(
						authorizableManager
								.findAuthorizable(
										propertyName,
										value,
										org.sakaiproject.nakamura.api.lite.authorizable.Group.class),
						authorizableManager, accessControlManager, valueFactory);
			case SEARCH_TYPE_USER:
				return new SparseAuthorizableIterator(
						authorizableManager
								.findAuthorizable(
										propertyName,
										value,
										org.sakaiproject.nakamura.api.lite.authorizable.User.class),
						authorizableManager, accessControlManager, valueFactory);
			default:
				throw new IllegalArgumentException("Invalid search type "
						+ searchType);
			}
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		}
	}

	public User createUser(String userID, String password)
			throws AuthorizableExistsException, RepositoryException {
		try {
			boolean created = authorizableManager.createUser(userID, userID,
					password, new HashMap<String, Object>());
			if (created) {
				return (User) getAuthorizable(userID);
			} else {
				throw new AuthorizableExistsException("User " + userID
						+ " already exits ");
			}
		} catch (AccessDeniedException e) {
			throw new javax.jcr.AccessDeniedException(e.getMessage(), e);
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		}
	}

	public User createUser(String userID, String password, Principal principal,
			String intermediatePath) throws AuthorizableExistsException,
			RepositoryException {
		try {
			boolean created = authorizableManager.createUser(userID,
					principal.getName(), password,
					new HashMap<String, Object>());
			if (created) {
				return (User) getAuthorizable(userID);
			} else {
				throw new AuthorizableExistsException("User " + userID
						+ " already exits ");
			}
		} catch (AccessDeniedException e) {
			throw new javax.jcr.AccessDeniedException(e.getMessage(), e);
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		}
	}

	public Group createGroup(Principal principal)
			throws AuthorizableExistsException, RepositoryException {
		try {
			String id = principal.getName();
			boolean created = authorizableManager.createGroup(
					principal.getName(), principal.getName(),
					new HashMap<String, Object>());
			if (created) {
				return (Group) getAuthorizable(id);
			} else {
				throw new AuthorizableExistsException("Group " + id
						+ " already exits ");
			}
		} catch (AccessDeniedException e) {
			throw new javax.jcr.AccessDeniedException(e.getMessage(), e);
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		}
	}

	public Group createGroup(Principal principal, String intermediatePath)
			throws AuthorizableExistsException, RepositoryException {
		return createGroup(principal);
	}

	public boolean isAutoSave() {
		return true;
	}

	public void autoSave(boolean enable)
			throws UnsupportedRepositoryOperationException, RepositoryException {
		throw new UnsupportedRepositoryOperationException();
	}

	public void loggingOut(SessionImpl session) {
	}

	public void loggedOut(SessionImpl session) {
		try {
			this.session.logout();
			int systemSessions = systemSessionCounter.get();
			int sessions = sessionCounter.get();
			if ( "org.apache.jackrabbit.core.SystemSession".equals(session.getClass().getName()) ) {
				systemSessions = systemSessionCounter.decrementAndGet();
			} else {
				sessions = sessionCounter.decrementAndGet();
			}
			LOGGER.info("Logged out of sparse triggered bu Session {} {} {} ",new Object[]{session, sessions, systemSessions});
			if ( session != jcrSession ) {
				LOGGER.warn("Odd session are not the same on login logout {} {} ",jcrSession,session);
			}
		} catch (ConnectionPoolException e) {
			LOGGER.error("Failed to logout ", e);
		}
	}

	public org.sakaiproject.nakamura.api.lite.Session getSession() {
		return session;
	}

}
