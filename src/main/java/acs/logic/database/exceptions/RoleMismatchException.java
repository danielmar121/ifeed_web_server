package acs.logic.database.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import acs.boundaries.details.UserId;

@ResponseStatus(code = HttpStatus.METHOD_NOT_ALLOWED)
public class RoleMismatchException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RoleMismatchException(UserId userId, String methodName) {
		super(String.format("%s user cannot invoke %s method.", userId.toString(), methodName));
	}
}
