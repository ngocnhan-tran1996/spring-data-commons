/*
 * Copyright 2011-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.transaction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.Assert;

/**
 * {@link TransactionStatus} implementation to orchestrate {@link TransactionStatus} instances for multiple
 * {@link PlatformTransactionManager} instances.
 *
 * @author Michael Hunger
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @since 1.6
 */
class MultiTransactionStatus implements TransactionStatus {

	private final PlatformTransactionManager mainTransactionManager;
	private final Map<PlatformTransactionManager, TransactionStatus> transactionStatuses = Collections
			.synchronizedMap(new HashMap<PlatformTransactionManager, TransactionStatus>());

	private boolean newSynchonization;

	/**
	 * Creates a new {@link MultiTransactionStatus} for the given {@link PlatformTransactionManager}.
	 *
	 * @param mainTransactionManager must not be {@literal null}.
	 */
	public MultiTransactionStatus(PlatformTransactionManager mainTransactionManager) {

		Assert.notNull(mainTransactionManager, "TransactionManager must not be null!");
		this.mainTransactionManager = mainTransactionManager;
	}

	public Map<PlatformTransactionManager, TransactionStatus> getTransactionStatuses() {
		return transactionStatuses;
	}

	public void setNewSynchonization() {
		this.newSynchonization = true;
	}

	public boolean isNewSynchonization() {
		return newSynchonization;
	}

	public void registerTransactionManager(TransactionDefinition definition, PlatformTransactionManager transactionManager) {
		getTransactionStatuses().put(transactionManager, transactionManager.getTransaction(definition));
	}

	public void commit(PlatformTransactionManager transactionManager) {
		TransactionStatus transactionStatus = getTransactionStatus(transactionManager);
		transactionManager.commit(transactionStatus);
	}

	/**
	 * Rolls back the {@link TransactionStatus} registered for the given {@link PlatformTransactionManager}.
	 *
	 * @param transactionManager must not be {@literal null}.
	 */
	public void rollback(PlatformTransactionManager transactionManager) {
		transactionManager.rollback(getTransactionStatus(transactionManager));
	}

	public boolean isRollbackOnly() {
		return getMainTransactionStatus().isRollbackOnly();
	}

	public boolean isCompleted() {
		return getMainTransactionStatus().isCompleted();
	}

	public boolean isNewTransaction() {
		return getMainTransactionStatus().isNewTransaction();
	}

	public boolean hasSavepoint() {
		return getMainTransactionStatus().hasSavepoint();
	}

	public void setRollbackOnly() {
		for (TransactionStatus ts : transactionStatuses.values()) {
			ts.setRollbackOnly();
		}
	}

	public Object createSavepoint() throws TransactionException {

		SavePoints savePoints = new SavePoints();

		for (TransactionStatus transactionStatus : transactionStatuses.values()) {
			savePoints.save(transactionStatus);
		}
		return savePoints;
	}

	public void rollbackToSavepoint(Object savepoint) throws TransactionException {
		SavePoints savePoints = (SavePoints) savepoint;
		savePoints.rollback();
	}

	public void releaseSavepoint(Object savepoint) throws TransactionException {
		((SavePoints) savepoint).release();
	}

	public void flush() {
		for (TransactionStatus transactionStatus : transactionStatuses.values()) {
			transactionStatus.flush();
		}
	}

	private TransactionStatus getMainTransactionStatus() {
		return transactionStatuses.get(mainTransactionManager);
	}

	private TransactionStatus getTransactionStatus(PlatformTransactionManager transactionManager) {
		return this.getTransactionStatuses().get(transactionManager);
	}

	private static class SavePoints {

		private final Map<TransactionStatus, Object> savepoints = new HashMap<>();

		private void addSavePoint(TransactionStatus status, Object savepoint) {

			Assert.notNull(status, "TransactionStatus must not be null!");
			this.savepoints.put(status, savepoint);
		}

		private void save(TransactionStatus transactionStatus) {
			Object savepoint = transactionStatus.createSavepoint();
			addSavePoint(transactionStatus, savepoint);
		}

		public void rollback() {
			for (TransactionStatus transactionStatus : savepoints.keySet()) {
				transactionStatus.rollbackToSavepoint(savepointFor(transactionStatus));
			}
		}

		private Object savepointFor(TransactionStatus transactionStatus) {
			return savepoints.get(transactionStatus);
		}

		public void release() {
			for (TransactionStatus transactionStatus : savepoints.keySet()) {
				transactionStatus.releaseSavepoint(savepointFor(transactionStatus));
			}
		}
	}
}
