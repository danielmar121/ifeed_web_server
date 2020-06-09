package acs.logic;

import java.util.Collection;

import acs.boundaries.UserBoundary;

public interface DBUserService extends UserService {

	Collection<UserBoundary> getAllUsers(String adminDomain, String adminEmail, int size, int page);

	boolean isManagerValidation(String managerDomain, String managerEmail);

	boolean isAdminValidation(String userDomain, String userEmail);

	boolean isPlayerValidation(String userDomain, String userEmail);
}
