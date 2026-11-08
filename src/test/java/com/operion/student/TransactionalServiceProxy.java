package com.operion.student;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * These tests build StudentService with {@code new}, so its {@code @Transactional} methods would otherwise run
 * without a transaction. promote() takes a pessimistic row lock, which requires one - this wraps the service the
 * way Spring does in the running app, so the lock and the rollback-on-rejection behave as in production.
 */
final class TransactionalServiceProxy {

	private TransactionalServiceProxy() {
	}

	static <T> T of(T service, PlatformTransactionManager transactionManager) {
		ProxyFactory proxyFactory = new ProxyFactory(service);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
		@SuppressWarnings("unchecked")
		T proxy = (T) proxyFactory.getProxy();
		return proxy;
	}
}
