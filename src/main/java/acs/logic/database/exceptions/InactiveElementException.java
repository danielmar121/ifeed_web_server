package acs.logic.database.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import acs.data.details.ElementEntityId;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class InactiveElementException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InactiveElementException(ElementEntityId elementId) {
		super(String.format("%s is an inactive entity.", elementId.toString()));
	}

}
