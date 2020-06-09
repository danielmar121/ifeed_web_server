package acs.logic.database.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import acs.data.details.ActionEntityId;
import acs.data.details.ElementEntityId;
import acs.data.details.UserEntityId;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 8563195120696546818L;

	private EntityNotFoundException(String objClass, String objString) {
		super(String.format("Entity of type %s: %s was not found.", objClass, objString));
	}

	public EntityNotFoundException(UserEntityId user) {
		this(user.getClass().getCanonicalName().replace("Id", ""), user.toString());
	}

	public EntityNotFoundException(ElementEntityId element) {
		this(element.getClass().getCanonicalName().replace("Id", ""), element.toString());
	}

	public EntityNotFoundException(ActionEntityId action) {
		this(action.getClass().getCanonicalName().replace("Id", ""), action.toString());
	}
}

