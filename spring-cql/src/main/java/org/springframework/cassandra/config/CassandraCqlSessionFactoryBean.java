/*
 * Copyright 2013-2014 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cassandra.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cassandra.core.CqlOperations;
import org.springframework.cassandra.core.CqlTemplate;
import org.springframework.cassandra.support.CassandraExceptionTranslator;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * Factory for creating and configuring a Cassandra {@link Session}, which is a thread-safe singleton.
 * As such, it is sufficient to have one {@link Session} per application and keyspace.
 *
 * @author Alex Shvid
 * @author Matthew T. Adams
 * @author John Blum
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.FactoryBean
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.dao.support.PersistenceExceptionTranslator
 * @see org.springframework.cassandra.core.CqlTemplate
 * @see org.springframework.cassandra.support.CassandraExceptionTranslator
 * @see com.datastax.driver.core.Cluster
 * @see com.datastax.driver.core.Session
 */
@SuppressWarnings("unused")
public class CassandraCqlSessionFactoryBean implements FactoryBean<Session>, InitializingBean, DisposableBean,
		PersistenceExceptionTranslator {

	private Cluster cluster;

	private List<String> startupScripts = Collections.emptyList();
	private List<String> shutdownScripts = Collections.emptyList();

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final PersistenceExceptionTranslator exceptionTranslator = new CassandraExceptionTranslator();

	private Session session;

	private String keyspaceName;

	/* (non-Javadoc) */
	@Override
	public Session getObject() {
		return this.session;
	}

	/* (non-Javadoc) */
	@Override
	public Class<? extends Session> getObjectType() {
		return (this.session != null ? this.session.getClass() : Session.class);
	}

	/* (non-Javadoc) */
	@Override
	public boolean isSingleton() {
		return true;
	}

	/* (non-Javadoc) */
	@Override
	public void afterPropertiesSet() throws Exception {
		this.session = connect(getKeyspaceName());
		executeScripts(getStartupScripts());
	}

	/* (non-Javadoc) */
	Session connect(String keyspaceName) {
		return (StringUtils.hasText(keyspaceName) ? getCluster().connect(keyspaceName) : getCluster().connect());
	}

	/* (non-Javadoc) */
	@Override
	public void destroy() throws Exception {
		executeScripts(getShutdownScripts());
		getSession().close();
	}

	/**
	 * Executes the given Cassandra CQL scripts.  The {@link Session} must be connected when this method is called.
	 */
	protected void executeScripts(List<String> scripts) {
		if (!CollectionUtils.isEmpty(scripts)) {
			CqlOperations template = newCqlOperations(getSession());

			for (String script : scripts) {
				logger.info("executing raw CQL [{}]", script);
				template.execute(script);
			}
		}
	}

	/* (non-Javadoc) */
	CqlOperations newCqlOperations(Session session) {
		return new CqlTemplate(session);
	}

	/* (non-Javadoc) */
	@Override
	public DataAccessException translateExceptionIfPossible(RuntimeException e) {
		return this.exceptionTranslator.translateExceptionIfPossible(e);
	}

	/**
	 * Null-safe operation to determine whether the Cassandra {@link Session} is connected or not.
	 *
	 * @return a boolean value indicating whether the Cassandra {@link Session} is connected.
	 * @see com.datastax.driver.core.Session#isClosed()
	 * @see #getObject()
	 */
	public boolean isConnected() {
		Session session = getObject();
		return !(session == null || session.isClosed());
	}

	/**
	 * Sets a reference to the Cassandra {@link Cluster} to use.
	 *
	 * @param cluster a reference to the Cassandra {@link Cluster} used by this application.
	 * @throws IllegalArgumentException if the {@link Cluster} reference is null.
	 * @see com.datastax.driver.core.Cluster
	 * @see #getCluster()
	 */
	public void setCluster(Cluster cluster) {
		Assert.notNull(cluster, "Cluster must not be null");
		this.cluster = cluster;
	}

	/**
	 * Returns a reference to the configured Cassandra {@link Cluster} used by this application.
	 *
	 * @return a reference to the configured Cassandra {@link Cluster}.
	 * @throws IllegalStateException if the reference to the {@link Cluster} was not properly initialized.
	 * @see com.datastax.driver.core.Cluster
	 * @see #setCluster(Cluster)
	 */
	protected Cluster getCluster() {
		Assert.state(this.cluster != null, "Cluster was not properly initialized");
		return this.cluster;
	}

	/**
	 * Sets the name of the Cassandra Keyspace to connect to.  Passing <code>null</code>, an empty String,
	 * or whitespace will cause the Cassandra System Keyspace to be used.
	 *
	 * @param keyspaceName a String indicating the name of the Keyspace in which to connect.
	 * @see #getKeyspaceName()
	 */
	public void setKeyspaceName(String keyspaceName) {
		this.keyspaceName = keyspaceName;
	}

	/**
	 * Gets the name of the Cassandra Keyspace to connect to.
	 *
	 * @return the name of the Cassandra Keyspace to connect to as a String.
	 * @see #setKeyspaceName(String)
	 */
	protected String getKeyspaceName() {
		return this.keyspaceName;
	}

	/**
	 * Returns a reference to the connected Cassandra {@link Session}.
	 *
	 * @return a reference to the connected Cassandra {@link Session}.
	 * @throws IllegalStateException if the Cassandra {@link Session} was not properly initialized.
	 * @see com.datastax.driver.core.Session
	 */
	protected Session getSession() {
		Session session = getObject();
		Assert.state(session != null, "Session was not properly initialized");
		return session;
	}

	/**
	 * Sets CQL scripts to be executed immediately after the session is connected.
	 */
	public void setStartupScripts(List<String> scripts) {
		this.startupScripts = (scripts != null ? new ArrayList<String>(scripts) : Collections.<String>emptyList());
	}

	/**
	 * Returns an unmodifiable list of startup scripts.
	 */
	public List<String> getStartupScripts() {
		return Collections.unmodifiableList(this.startupScripts);
	}

	/**
	 * Sets CQL scripts to be executed immediately before the session is shutdown.
	 */
	public void setShutdownScripts(List<String> scripts) {
		this.shutdownScripts = (scripts != null ? new ArrayList<String>(scripts) : Collections.<String>emptyList());
	}

	/**
	 * Returns an unmodifiable list of shutdown scripts.
	 */
	public List<String> getShutdownScripts() {
		return Collections.unmodifiableList(this.shutdownScripts);
	}
}
