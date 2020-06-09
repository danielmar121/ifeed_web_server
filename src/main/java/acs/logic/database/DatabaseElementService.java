package acs.logic.database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import acs.aop.MonitorPerformance;
import acs.boundaries.ElementBoundary;
import acs.boundaries.details.CreatedBy;
import acs.boundaries.details.ElementId;
import acs.boundaries.details.UserId;
import acs.dal.ElementDao;
import acs.data.ElementEntity;
import acs.data.details.ElementEntityId;
import acs.logic.DBElementService;
import acs.logic.DBUserService;
import acs.logic.database.exceptions.EntityNotFoundException;
import acs.logic.database.exceptions.PaginationException;
import acs.logic.database.exceptions.RoleMismatchException;
import acs.logic.util.ElementConverter;

@Service
public class DatabaseElementService implements DBElementService {
	private ElementDao elementDao;
	private ElementConverter elementConverter;
	private DBUserService userService;

	@Value("${spring.application.name:default}")
	private String appDomain;

	@Autowired
	public DatabaseElementService(ElementDao elementDao, ElementConverter elementConverter, DBUserService userService) {
		super();
		this.elementDao = elementDao;
		this.elementConverter = elementConverter;
		this.userService = userService;
	}

	@PostConstruct
	public void init() {
	}

	private boolean isElementActive(ElementBoundary elementBoundary) {
		return (elementBoundary != null && elementBoundary.getActive());
	}

	@Override
	@Transactional
	@MonitorPerformance
	public ElementBoundary create(String managerDomain, String managerEmail, ElementBoundary elementBoundary)
			throws RuntimeException {
		if (this.userService.isManagerValidation(managerDomain, managerEmail)) {
			if (elementBoundary.getElementId() == null) {
				CreatedBy created = new CreatedBy(new UserId(managerDomain, managerEmail));
				elementBoundary.setCreatedBy(created);
				elementBoundary.setCreatedTimestamp(new Date());
				elementBoundary.setElementId(new ElementId(appDomain, UUID.randomUUID().toString()));
				ElementEntity elementEntity = this.elementConverter.toEntity(elementBoundary);
				this.elementDao.save(elementEntity);
				return this.elementConverter.toBoundary(elementEntity);
			} else {
				throw new RuntimeException(String.format(
						"Cannot create an ElementEntity with ElementId: %s. ElementId must be defined as null.",
						elementBoundary.getElementId().toString()));
			}

		}

		throw new RoleMismatchException(new UserId(managerDomain, managerEmail), "createElement");
	}

	@Override
	@Transactional
	@MonitorPerformance
	public ElementBoundary update(String managerDomain, String managerEmail, String elementDomain, String elementId,
			ElementBoundary update) {
		if (this.userService.isManagerValidation(managerDomain, managerEmail)) {
			ElementEntityId elemId = new ElementEntityId(elementDomain, elementId);
			ElementEntity elementEntity = this.getEntityElementFromDB(elemId);
			if (elementEntity != null) {
				if (update.getType() != null) {
					elementEntity.setType(update.getType());
				}
				if (update.getName() != null) {
					elementEntity.setName(update.getName());
				}
				if (update.getActive() != null) {
					elementEntity.setActive(update.getActive());
				}
				if (update.getLocation() != null) {
					elementEntity.setLat(update.getLocation().getLat());
					elementEntity.setLng(update.getLocation().getLng());
				}
				if (update.getElementAttributes() != null) {
					elementEntity.setElementAttributes(update.getElementAttributes());
				}
				this.elementDao.save(elementEntity);
				return this.elementConverter.toBoundary(elementEntity);
			} else {
				throw new EntityNotFoundException(elemId);
			}
		} else {
			throw new RoleMismatchException(new UserId(managerDomain, managerEmail), "updateElement");
		}
	}

	private ElementEntity getEntityElementFromDB(ElementEntityId elemId) {
		return this.elementDao.findById(elemId).orElseThrow(() -> new EntityNotFoundException(elemId));
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public List<ElementBoundary> getAll(String userDomain, String userEmail) {
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			return StreamSupport.stream(this.elementDao.findAll().spliterator(), false)
					.map(this.elementConverter::toBoundary).collect(Collectors.toList());
		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			return StreamSupport.stream(this.elementDao.findAll().spliterator(), false)
					.map(this.elementConverter::toBoundary).filter(e -> isElementActive(e) == true)
					.collect(Collectors.toList());
		} else // Admin
		{
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getSpecificElement");
		}
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ElementBoundary> getAll(String userDomain, String userEmail, int size, int page) {
		List<ElementEntity> entities;
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			entities = this.elementDao
					.findAll(
							PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"))
					.getContent();
		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			entities = this.elementDao
					.findByActiveTrue(
							PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"))
					.getContent();
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getAllElements");
		}
		return entities.stream().map(this.elementConverter::toBoundary).collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public ElementBoundary getSpecificElement(String userDomain, String userEmail, String elementDomain,
			String elementId) {
		ElementEntityId elemId = new ElementEntityId(elementDomain, elementId);
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			return this.elementConverter.toBoundary(getEntityElementFromDB(elemId));
		}
		if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			ElementBoundary elementBoundary = this.elementConverter.toBoundary(getEntityElementFromDB(elemId));
			if (isElementActive(elementBoundary)) {
				return elementBoundary;
			} else {
				throw new EntityNotFoundException(elemId);
			}
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getSpecificElement");
		}

	}

	@Override
	@Transactional
	@MonitorPerformance
	public void deleteAllElements(String adminDomain, String adminEmail) {
		if (this.userService.isAdminValidation(adminDomain, adminEmail)) {
			this.elementDao.deleteAll();
		} else {
			throw new RoleMismatchException(new UserId(adminDomain, adminEmail), "deteleAllElements Method");
		}
	}

	@Override
	@Transactional
	@MonitorPerformance
	public void bindExistingElementToAnExistingChildElement(String managerDomain, String managerEmail,
			String elementDomain, String elementId, ElementId elementChildId) {
		if (this.userService.isManagerValidation(managerDomain, managerEmail)) {
			ElementEntityId fatherId = new ElementEntityId(elementDomain, elementId);
			ElementEntityId childId = this.elementConverter.toEntityId(elementChildId);

			ElementEntity father = this.elementDao.findById(fatherId)
					.orElseThrow(() -> new EntityNotFoundException(fatherId));

			ElementEntity child = this.elementDao.findById(childId)
					.orElseThrow(() -> new EntityNotFoundException(childId));

			father.addChild(child);
			this.elementDao.save(father);
		} else {
			throw new RoleMismatchException(new UserId(managerDomain, managerEmail),
					"bindExistingElementToAnExistingChildElement");
		}
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ElementBoundary> getAllChildren(String userDomain, String userEmail, String elementDomain,
			String elementId, int size, int page) {
		List<ElementEntity> entities;
		ElementEntityId fatherId = new ElementEntityId(elementDomain, elementId);
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findAllByFather_ElementId(fatherId,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findAllByFather_ElementIdAndActiveTrue(fatherId,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getAllChildren");
		}
		return entities.stream().map(this.elementConverter::toBoundary).collect(Collectors.toSet());
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ElementBoundary> getParent(String userDomain, String userEmail, String elementDomain,
			String elementId, int size, int page) {
		List<ElementBoundary> fatherAsList = new ArrayList<>();

		ElementEntityId childId = this.elementConverter.toEntityId(new ElementId(elementDomain, elementId));

		if (size <= 0 || page < 0) {
			throw new PaginationException(page, size);
		}

		ElementEntity child = this.elementDao.findById(childId).orElseThrow(() -> new EntityNotFoundException(childId));

		ElementEntity father = child.getFather();

		if (this.userService.isManagerValidation(userDomain, userEmail)
				|| (this.userService.isPlayerValidation(userDomain, userEmail) && father.getActive())) {
			fatherAsList.add(this.elementConverter.toBoundary(father));
		}
		return fatherAsList;
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public List<ElementBoundary> getElementsWithElementName(String userDomain, String userEmail, String name, int size,
			int page) {
		List<ElementEntity> entities;
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findAllByNameLike(name,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));

		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findAllByNameLikeAndActiveTrue(name,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getElementsWithElementName");
		}
		return entities.stream().map(this.elementConverter::toBoundary).collect(Collectors.toList());

	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ElementBoundary> getElementsWithElementType(String userDomain, String userEmail, String type,
			int size, int page) {
		List<ElementEntity> entities;
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findAllByTypeLike(type,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findAllByTypeLikeAndActiveTrue(type,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getElementsWithElementType");
		}
		return entities.stream().map(this.elementConverter::toBoundary).collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ElementBoundary> getElementsNearBy(String userDomain, String userEmail, double lat, double lng,
			double distance, int size, int page) {
		List<ElementEntity> entities;
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findByLatBetweenAndLngBetween(lat - distance, lat + distance, lng - distance,
					lng + distance,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findByLatBetweenAndLngBetweenAndActiveTrue(lat - distance, lat + distance,
					lng - distance, lng + distance,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getElementsNearBy");
		}
		return entities.stream().map(this.elementConverter::toBoundary).collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ElementBoundary> getElementsWithElementTypeNearBy(String userDomain, String userEmail, double lat,
			double lng, double distance, String type, int size, int page) {
		List<ElementEntity> entities;
		if (this.userService.isManagerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findByLatBetweenAndLngBetweenAndTypeLike(lat - distance, lat + distance,
					lng - distance, lng + distance, type,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));

		} else if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			entities = this.elementDao.findByLatBetweenAndLngBetweenAndTypeLikeAndActiveTrue(lat - distance,
					lat + distance, lng - distance, lng + distance, type,
					PageRequest.of(page, size, Direction.ASC, "elementId.elementDomain", "elementId.elementId"));
		} else {
			throw new RoleMismatchException(new UserId(userDomain, userEmail), "getElementsWithElementTypeNearby");
		}
		return entities.stream().map(this.elementConverter::toBoundary).collect(Collectors.toList());
	}

}
