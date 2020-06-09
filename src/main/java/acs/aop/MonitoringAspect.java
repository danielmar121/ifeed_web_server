package acs.aop;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class MonitoringAspect {
	private Log logger;

	@PostConstruct
	public void init() {
		this.logger = LogFactory.getLog(MonitoringAspect.class);
	}

	@Around("@annotation(acs.aop.MonitorPerformance)")
	public Object measureOverallElapsedTime(ProceedingJoinPoint joinPoint) throws Throwable {
		// Pre-processing
		long beginTime = System.currentTimeMillis();

		// Invoke the original method
		try {
			Object rv = joinPoint.proceed();
			return rv;
		} finally {
			// Post-processing
			long endTime = System.currentTimeMillis();
			long elapsed = endTime - beginTime;

			// Add the name of the class and its method
			String fullyQualifiedNameOfClass = joinPoint.getTarget().getClass() // java.lang.Class
					.getSimpleName(); // Java Reflection

			// Add the method name to output
			String methodName = joinPoint.getSignature().getName();
			// print message to debug of the log
			this.logger.debug(fullyQualifiedNameOfClass + "." + methodName + "() - elapsed time: " + elapsed + "[ms]");
		}
	}
	
	
	@Around("@annotation(acs.aop.ValidateRole)")
	public Object validateRole(ProceedingJoinPoint joinPoint) throws Throwable {
		// Pre-processing
		long beginTime = System.currentTimeMillis();

		// Invoke the original method
		try {
			Object rv = joinPoint.proceed();
			return rv;
		} finally {
			// Post-processing
			long endTime = System.currentTimeMillis();
			long elapsed = endTime - beginTime;

			// Add the name of the class and its method
			String fullyQualifiedNameOfClass = joinPoint.getTarget().getClass() // java.lang.Class
					.getSimpleName(); // Java Reflection

			// Add the method name to output
			String methodName = joinPoint.getSignature().getName();
			// print message to debug of the log
			this.logger.debug(fullyQualifiedNameOfClass + "." + methodName + "() - elapsed time: " + elapsed + "[ms]");
		}
	}

}
