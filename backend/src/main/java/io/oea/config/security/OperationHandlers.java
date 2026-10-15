package io.oea.config.security;

import java.lang.reflect.Method;
import org.springframework.web.method.HandlerMethod;

/** Maps handler methods to contract operations: the method of a generated interface is the operationId. */
public final class OperationHandlers {

	static final String GENERATED_PACKAGE = "io.oea.api.generated";

	private OperationHandlers() {
	}

	/** True if the handler implements (or inherits) a method of a generated API interface. */
	public static boolean isGeneratedOperation(HandlerMethod handler) {
		Method method = handler.getMethod();
		for (Class<?> type : handler.getBeanType().getInterfaces()) {
			if (type.getPackageName().equals(GENERATED_PACKAGE) && declares(type, method)) {
				return true;
			}
		}
		return method.getDeclaringClass().getPackageName().equals(GENERATED_PACKAGE);
	}

	private static boolean declares(Class<?> type, Method method) {
		try {
			type.getMethod(method.getName(), method.getParameterTypes());
			return true;
		}
		catch (NoSuchMethodException ex) {
			return false;
		}
	}

}
