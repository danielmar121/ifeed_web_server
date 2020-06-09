package acs.logic.database;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import acs.aop.MonitorPerformance;
import acs.boundaries.UserBoundary;
import acs.boundaries.details.UserId;
import acs.dal.UserDao;
import acs.data.UserEntity;
import acs.data.UserRole;
import acs.data.details.UserEntityId;
import acs.logic.DBUserService;
import acs.logic.database.exceptions.EntityDuplicationException;
import acs.logic.database.exceptions.EntityNotFoundException;
import acs.logic.database.exceptions.RoleMismatchException;
import acs.logic.util.UserConverter;

@Service
public class DatabaseUserService implements DBUserService {

	private UserConverter userConverter;
	private UserDao userDao;
	@Value("${spring.application.name:default}")
	private String appDomain;

	@Autowired
	public DatabaseUserService(UserDao userDao, UserConverter userConverter) {
		this.userDao = userDao;
		this.userConverter = userConverter;
	}

	// all UPSET queries
	@Override
	@Transactional
	@MonitorPerformance
	public UserBoundary createUser(UserBoundary user) {
		UserEntityId key = new UserEntityId(this.appDomain, user.getUserId().getEmail());
		if (getEntityUserFromDatabase(key) != null) {
			throw new EntityDuplicationException(key);
		}

		UserEntity newUserEntity = this.userConverter.toEntity(user);

		this.userDao.save(newUserEntity);
		return this.userConverter.toBoundary(getEntityUserFromDatabase(key));
	}

	@Override
	@Transactional
	@MonitorPerformance
	public UserBoundary updateUser(String userDomain, String userEmail, UserBoundary updatedUserBoundary) {
		UserEntityId userId = new UserEntityId(userDomain, userEmail);
		UserEntity userEntityToBeUpdated = getEntityUserFromDatabase(userId);

		if (userEntityToBeUpdated == null) {
			throw new EntityNotFoundException(userId);
		}

		UserEntity userEntityUpdates = this.userConverter.toEntity(updatedUserBoundary);

		// Update Name:
		userEntityToBeUpdated.setUserName(userEntityUpdates.getUserName() == null ? userEntityToBeUpdated.getUserName()
				: userEntityUpdates.getUserName());
		// Update Role:
		userEntityToBeUpdated.setRole(
				userEntityUpdates.getRole() == null ? userEntityToBeUpdated.getRole() : userEntityUpdates.getRole());
		// Update Avatar:
		userEntityToBeUpdated.setAvatar(userEntityUpdates.getAvatar() == null ? userEntityToBeUpdated.getAvatar()
				: userEntityUpdates.getAvatar());

		return this.userConverter.toBoundary(this.userDao.save(userEntityToBeUpdated));
	}

	// all GET queries
	private UserEntity getEntityUserFromDatabase(UserEntityId id) {
		return this.userDao.findById(id).orElse(null);
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public UserBoundary login(String userDomain, String userEmail) {
		UserEntity entity = getEntityUserFromDatabase(new UserEntityId(userDomain, userEmail));
		return this.userConverter.toBoundary(entity);
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public List<UserBoundary> getAllUsers(String adminDomain, String adminEmail) {
		if (isAdminValidation(adminDomain, adminEmail)) {
			return StreamSupport.stream(this.userDao.findAll().spliterator(), false).map(this.userConverter::toBoundary)
					.collect(Collectors.toList());
		} else {
			throw new RoleMismatchException(new UserId(adminDomain, adminEmail), "getAllUsers");
		}
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<UserBoundary> getAllUsers(String userDomain, String userEmail, int size, int page) {
		if (isAdminValidation(userDomain, userEmail)) {
			return this.userDao.findAll(PageRequest.of(page, size, Direction.ASC, "id.userDomain", "id.email"))
					.getContent().stream().map(this.userConverter::toBoundary).collect(Collectors.toList());
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getAllUsers");
		}
	}

	// all DELETE queries
	@Override
	@Transactional
	@MonitorPerformance
	public void deleteAllUsers(String adminDomain, String adminEmail) {
		if (isAdminValidation(adminDomain, adminEmail)) {
			this.userDao.deleteAll();
		} else {
			throw new RoleMismatchException(new UserId(adminDomain, adminEmail), "deleteAllUsers");
		}
	}

	// all validate user role functions
	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public boolean isManagerValidation(String managerDomain, String managerEmail) {
		UserEntity userInSystem = getEntityUserFromDatabase(new UserEntityId(managerDomain, managerEmail));
		return (userInSystem != null && userInSystem.getRole().equals(UserRole.MANAGER));
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public boolean isAdminValidation(String adminDomain, String adminEmail) {
		UserEntity userInSystem = getEntityUserFromDatabase(new UserEntityId(adminDomain, adminEmail));
		return (userInSystem != null && userInSystem.getRole().equals(UserRole.ADMIN));
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public boolean isPlayerValidation(String playerDomain, String playerEmail) {
		UserEntity userInSystem = getEntityUserFromDatabase(new UserEntityId(playerDomain, playerEmail));
		return (userInSystem != null && userInSystem.getRole().equals(UserRole.PLAYER));
	}

}
