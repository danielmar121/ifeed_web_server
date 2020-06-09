package acs.logic.database.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NO_CONTENT)
public class PaginationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PaginationException(int page, int size) {
		super(String.format("Cannot reach page %d with page-size of %d.", page, size));
	}

}
