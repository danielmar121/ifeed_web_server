package acs.logic.database.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import acs.data.details.UserEntityId;

@ResponseStatus(code = HttpStatus.ALREADY_REPORTED)
public class EntityDuplicationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private EntityDuplicationException(String entityIdString) {
		super(String.format("%s already exists in the database.", entityIdString));
	}

	public EntityDuplicationException(UserEntityId userId) {
		this(userId.toString());
	}

}
