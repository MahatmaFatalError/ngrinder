package org.ngrinder.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ngrinder.common.exception.NGrinderRuntimeException;
import org.ngrinder.common.util.PropertiesWrapper;
import org.ngrinder.infra.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;

import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.ngrinder.common.constant.LdapConstants.*;

@Component
@RequiredArgsConstructor
public class NGrinderLdapContext {
	private static final Logger log = LoggerFactory.getLogger(NGrinderLdapContext.class);

	private static final String LDAP_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
	private static final String LDAP_AUTH_SIMPLE = "Simple";
	private static final String LDAP_AUTH_NONE = "None";
	private static final int LDAP_SEARCH_COUNT_UNLIMITED = 0;

	private final ApplicationContext applicationContext;
	private final Config config;

	@Getter
	private LdapContext ldapContext;
	@Getter
	private SearchControls searchControls;

	@PostConstruct
	public void init() {
		config.addSystemConfListener(event -> initialize());
		initialize();
	}

	private void initialize() {
		ldapContext = createLdapContext();

		if (ldapContext != null) {
			log.info("LDAP login is enabled");
			applicationContext.getAutowireCapableBeanFactory().autowireBean(DefaultLdapLoginPlugin.class);
		}

		searchControls = new SearchControls();
		searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		searchControls.setTimeLimit(Integer.parseInt(config.getLdapProperties().getProperty(PROP_LDAP_SEARCH_TIME_LIMIT)));
		searchControls.setCountLimit(LDAP_SEARCH_COUNT_UNLIMITED);
	}

	private LdapContext createLdapContext() {
		boolean enabled = config.getLdapProperties().getPropertyBoolean(PROP_LDAP_ENABLED, false);
		if (!enabled) {
			log.info("LDAP login is disabled");
			return null;
		}

		String serverAddress = config.getLdapProperties().getProperty(PROP_LDAP_SERVER);
		if (serverAddress == null) {
			log.info("LDAP server is not specified. LDAP login is disabled");
			return null;
		}

		try {
			return new InitialLdapContext(getLdapEnvironment(), null);
		} catch (NamingException e) {
			throw new NGrinderRuntimeException(e);
		}
	}

	private Hashtable<?, ?> getLdapEnvironment() {
		PropertiesWrapper ldapProperties = config.getLdapProperties();
		Hashtable<String, String> env = new Hashtable<>();

		env.put(Context.PROVIDER_URL, ldapProperties.getProperty(PROP_LDAP_SERVER));
		env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_FACTORY);
		env.put(Context.SECURITY_AUTHENTICATION, LDAP_AUTH_NONE);

		String managerDn = ldapProperties.getProperty(PROP_LDAP_MANAGER_DN);
		String managerPassword = ldapProperties.getProperty(PROP_LDAP_MANAGER_PASSWORD);

		if (isNotEmpty(managerDn) && isNotEmpty(managerPassword)) {
			env.put(Context.SECURITY_AUTHENTICATION, LDAP_AUTH_SIMPLE);
			env.put(Context.SECURITY_PRINCIPAL, managerDn);
			env.put(Context.SECURITY_CREDENTIALS, managerPassword);
		}

		return env;
	}

	public String getUserNameKey() {
		return config.getLdapProperties().getProperty(PROP_LDAP_USER_DISPLAY_NAME);
	}

	public String getUserEmailKey() {
		return config.getLdapProperties().getProperty(PROP_LDAP_USER_EMAIL);
	}

	public String getBaseDN() {
		return config.getLdapProperties().getProperty(PROP_LDAP_BASE_DN, "");
	}

	public String getUserSearchBase() {
		return config.getLdapProperties().getProperty(PROP_LDAP_USER_SEARCH_BASE, "");
	}

	public String getUserFilter() {
		return config.getLdapProperties().getProperty(PROP_LDAP_USER_SEARCH_FILTER);
	}
}