package acs.logic.database;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import acs.boundaries.ActionBoundary;
import acs.boundaries.ElementBoundary;
import acs.boundaries.details.ActionId;
import acs.boundaries.details.InvokedBy;
import acs.boundaries.details.Location;
import acs.boundaries.details.UserId;
import acs.dal.ActionDao;
import acs.data.ActionEntity;
import acs.data.details.UserEntityId;
import acs.logic.DBActionService;
import acs.logic.DBElementService;
import acs.logic.DBUserService;
import acs.logic.database.exceptions.EntityDuplicationException;
import acs.logic.database.exceptions.RoleMismatchException;
import acs.logic.util.ActionConverter;
import acs.logic.util.DatePattern;

@Service
public class DatabaseActionService implements DBActionService {

	@Value("${spring.application.name:default}")
	private String appDomain;

	private ActionDao actionDao;

	private ActionConverter actionConverter;

	private DBElementService elementService;

	private DBUserService userService;

	enum Operation {
		CREATE, REMOVE, UPDATE
	};

	@Autowired
	public DatabaseActionService(ActionDao actionDao, ActionConverter actionConverter, DBElementService elementService,
			DBUserService userService) {
		super();
		this.actionDao = actionDao;
		this.actionConverter = actionConverter;
		this.elementService = elementService;
		this.userService = userService;
	}

	@PostConstruct
	public void init() {
	}

	@Override
	@Transactional
	@MonitorPerformance
	public Object invokeAction(ActionBoundary action) {
		if (action.getActionId() == null
				|| (action.getActionId().getDomain() == null && action.getActionId().getId() == null)) {
			if (invokedActionIsValid(action)) {
				ActionId actionId = new ActionId(appDomain, UUID.randomUUID().toString());
				action.setActionId(actionId);
				action.setCreatedTimestamp(new Date());
				ActionEntity actionEntity = this.actionConverter.toEntity(action);
				actionDao.save(actionEntity);
				return this.actionConverter.toBoundary(actionEntity);
			} else {
				throw new RoleMismatchException(action.getInvokedBy().getUserId(), "invokeAction");
			}
		} else {
			throw new EntityDuplicationException(new UserEntityId(action.getInvokedBy().getUserId().getDomain(),
					action.getInvokedBy().getUserId().getEmail()));
		}
	}

	public boolean invokedActionIsValid(ActionBoundary actionBoundary) {
		InvokedBy invoked = actionBoundary.getInvokedBy();
		if (invoked == null)
			return false;
		String userDomain = invoked.getUserId().getDomain();
		String userEmail = invoked.getUserId().getEmail();
		if (this.userService.isPlayerValidation(userDomain, userEmail)) {
			ElementBoundary elementBoundary = getElementBoundaryFromActionBoundary(actionBoundary);
			if (elementBoundary != null) {
				return true;
			}
		}
		return false;
	}

	private ElementBoundary getElementBoundaryFromActionBoundary(ActionBoundary actionBoundary) {
		String actionType = actionBoundary.getType();

		switch (actionType) {
		case "add-food_bowl": {
			return createFoodBowl(actionBoundary);
		}
		case "add-water_bowl": {
			return createWaterBowl(actionBoundary);
		}
		case "add-feeding_area": {
			return createFeedingArea(actionBoundary);
		}
		case "refill-food_bowl": {
			return refillFoodBowl(actionBoundary);
		}
		case "refill-water_bowl": {
			return refillWaterBowl(actionBoundary);
		}
		case "remove-food_bowl": {
			return removeFoodBowl(actionBoundary);
		}
		case "remove-water_bowl": {
			return removeWaterBowl(actionBoundary);
		}
		case "remove-feeding_area": {
			return removeFeedingArea(actionBoundary);
		}
		default:
			return null;
		}
	}

	/*
	 * For a creation of ElementBoundary, this function uses the "actionAttributes"
	 * field of the input: actionBoundary.
	 * 
	 */
	private ElementBoundary createElementBoundary(ActionBoundary actionBoundary) {

		Map<String, Object> attributes = actionBoundary.getActionAttributes();
		ElementBoundary element = findElementBoundaryByElementIdField(actionBoundary);

		if (element.getActive() == true) {
			String elementType = actionBoundary.getType().split("-")[1];
			String elementName = attributes.get("elementName").toString();
			String managerDomain = attributes.get("managerDomain").toString();
			String managerEmail = attributes.get("managerEmail").toString();
			Double lat = Double.parseDouble(attributes.get("elementLat").toString());
			Double lng = Double.parseDouble(attributes.get("elementLng").toString());
			Location location = new Location(lat, lng);

			Map<String, Object> elementAttributes = new TreeMap<>(attributes);
			elementAttributes.remove("managerDomain");
			elementAttributes.remove("managerEmail");
			elementAttributes.remove("elementName");
			elementAttributes.remove("elementLat");
			elementAttributes.remove("elementLng");

			ElementBoundary elementBoundary = new ElementBoundary(null, elementType, elementName, true, null, null,
					location, elementAttributes);

			ElementBoundary createdElement = elementService.create(managerDomain, managerEmail, elementBoundary);
			if (createdElement != null) {
				this.elementService.bindExistingElementToAnExistingChildElement(managerDomain, managerEmail,
						actionBoundary.getElement().getElementId().getDomain(),
						actionBoundary.getElement().getElementId().getId(), createdElement.getElementId());
				return createdElement;
			}
		}
		return null;
	}

	private ElementBoundary updateElementBoundary(ActionBoundary actionBoundary) {
		ElementBoundary elementBoundary = findElementBoundaryByElementIdField(actionBoundary);
		if (elementBoundary != null && elementBoundary.getActive() == true) {
			Map<String, Object> attributes = actionBoundary.getActionAttributes();
			String managerDomain = attributes.get("managerDomain").toString();
			String managerEmail = attributes.get("managerEmail").toString();
			Map<String, Object> elementAttributes = new TreeMap<>(attributes);
			elementAttributes.remove("managerDomain");
			elementAttributes.remove("managerEmail");
			elementAttributes.remove("elementName");
			elementAttributes.remove("elementLat");
			elementAttributes.remove("elementLng");
			elementBoundary.setElementAttributes(elementAttributes);
			ElementBoundary updatedElementBoundary = elementService.update(managerDomain, managerEmail,
					elementBoundary.getElementId().getDomain(), elementBoundary.getElementId().getId(),
					elementBoundary);
			return updatedElementBoundary;
		}
		return null;
	}

	private ElementBoundary findElementBoundaryByElementIdField(ActionBoundary actionBoundary) {
		String userDomain = actionBoundary.getInvokedBy().getUserId().getDomain();
		String userEmail = actionBoundary.getInvokedBy().getUserId().getEmail();
		String elementDomain = actionBoundary.getElement().getElementId().getDomain();
		String elementId = actionBoundary.getElement().getElementId().getId();
		return elementService.getSpecificElement(userDomain, userEmail, elementDomain, elementId);
	}

	private ElementBoundary removeElementBoundary(ActionBoundary actionBoundary) {
		ElementBoundary elementBoundary = findElementBoundaryByElementIdField(actionBoundary);
		if (elementBoundary != null && elementBoundary.getActive() == true) {
			elementBoundary.setActive(false);
			Map<String, Object> attributes = actionBoundary.getActionAttributes();
			String managerDomain = attributes.get("managerDomain").toString();
			String managerEmail = attributes.get("managerEmail").toString();

			ElementBoundary updatedElementBoundary = elementService.update(managerDomain, managerEmail,
					elementBoundary.getElementId().getDomain(), elementBoundary.getElementId().getId(),
					elementBoundary);

			if (updatedElementBoundary != null) {
				String userDomain = actionBoundary.getInvokedBy().getUserId().getDomain();
				String userEmail = actionBoundary.getInvokedBy().getUserId().getEmail();
				removeChildrenOf(userDomain, userEmail, managerDomain, managerEmail, updatedElementBoundary);
			}
			return updatedElementBoundary;
		}
		return null;

	}

	private void removeChildrenOf(String userDomain, String userEmail, String managerDomain, String managerEmail,
			ElementBoundary updatedElementBoundary) {
		int page = 0;
		String elementDomain = updatedElementBoundary.getElementId().getDomain();
		String elementId = updatedElementBoundary.getElementId().getId();
		while (true) {
			Collection<ElementBoundary> children = elementService.getAllChildren(userDomain, userEmail, elementDomain,
					elementId, 20, page);
			if (children.isEmpty()) {
				break;
			} else {
				children.forEach(child -> {
					child.setActive(false);
					this.elementService.update(managerDomain, managerEmail, child.getElementId().getDomain(),
							child.getElementId().getId(), child);
				});
			}
			page += 1;
		}
	}

	private ElementBoundary createFoodBowl(ActionBoundary actionBoundary) {
		if (isFoodBowl(actionBoundary.getActionAttributes())) {
			ElementBoundary foodBowl = createElementBoundary(actionBoundary);
			updateFeedingAreaStatus(foodBowl, Operation.CREATE);
			return foodBowl;
		}
		return null;
	}

	private ElementBoundary createWaterBowl(ActionBoundary actionBoundary) {
		if (isWaterBowl(actionBoundary.getActionAttributes())) {
			ElementBoundary waterBowl = createElementBoundary(actionBoundary);
			updateFeedingAreaStatus(waterBowl, Operation.CREATE);
			return waterBowl;
		}
		return null;
	}

	private ElementBoundary createFeedingArea(ActionBoundary actionBoundary) {
		if (isFeedingArea(actionBoundary.getActionAttributes())) {
			return createElementBoundary(actionBoundary);
		}
		return null;
	}

	private ElementBoundary refillFoodBowl(ActionBoundary actionBoundary) {
		if (isFoodBowl(actionBoundary.getActionAttributes())) {
			Boolean prevFoodBowlState = Boolean.parseBoolean(
					findElementBoundaryByElementIdField(actionBoundary).getElementAttributes().get("state").toString());
			ElementBoundary foodBowl = updateElementBoundary(actionBoundary);
			if (foodBowl != null) {
				if (prevFoodBowlState.booleanValue() != Boolean
						.parseBoolean(foodBowl.getElementAttributes().get("state").toString())) {
					updateFeedingAreaStatus(foodBowl, Operation.UPDATE);
				}
			}
			return foodBowl;
		}
		return null;
	}

	private ElementBoundary refillWaterBowl(ActionBoundary actionBoundary) {
		if (isWaterBowl(actionBoundary.getActionAttributes())) {
			Boolean prevWaterBowlState = Boolean.parseBoolean(
					findElementBoundaryByElementIdField(actionBoundary).getElementAttributes().get("state").toString());
			ElementBoundary waterBowl = updateElementBoundary(actionBoundary);
			if (waterBowl != null) {
				if (prevWaterBowlState.booleanValue() != Boolean
						.parseBoolean(waterBowl.getElementAttributes().get("state").toString())) {
					updateFeedingAreaStatus(waterBowl, Operation.UPDATE);
				}
			}
			return waterBowl;
		}
		return null;
	}

	private Boolean updateFeedingAreaStatus(ElementBoundary bowl, Operation op) {
		ElementBoundary father = this.elementService
				.getParent(bowl.getCreatedBy().getUserId().getDomain(), bowl.getCreatedBy().getUserId().getEmail(),
						bowl.getElementId().getDomain(), bowl.getElementId().getId(), 1, 0)
				.stream().findFirst().orElse(null);
		if (father != null) {
			Map<String, Object> elementAttributes = updateBowlsState(father, bowl, op);
			father.setElementAttributes(elementAttributes);
			this.elementService.update(father.getCreatedBy().getUserId().getDomain(),
					father.getCreatedBy().getUserId().getEmail(), father.getElementId().getDomain(),
					father.getElementId().getId(), father);
			return true;
		}
		return false;
	}

	private Map<String, Object> updateBowlsState(ElementBoundary feedingArea, ElementBoundary bowl, Operation op) {
		Map<String, Object> elementAttributes = feedingArea.getElementAttributes();
		Boolean bowlState = Boolean.parseBoolean(bowl.getElementAttributes().get("state").toString());
		int newVal = 0;
		switch (bowl.getType()) {
		case "food_bowl":
			newVal = (int) elementAttributes.get("fullFoodBowl") + byOperation(op, bowlState);
			elementAttributes.put("fullFoodBowl", newVal < 0 ? 0 : newVal);
			break;
		case "water_bowl":
			newVal = (int) elementAttributes.get("fullWaterBowl") + byOperation(op, bowlState);
			elementAttributes.put("fullWaterBowl", newVal < 0 ? 0 : newVal);
			break;
		}
		return elementAttributes;
	}

	private Integer byOperation(Operation op, Boolean bowlState) {
		Integer ret = 0;
		switch (op) {
		case CREATE:
			if (bowlState) {
				ret = 1;
			} else {
				ret = 0;
			}
			break;
		case UPDATE:
			if (bowlState) {
				ret = 1;
			} else {
				ret = -1;
			}
			break;
		case REMOVE:
			if (bowlState) {
				ret = -1;
			} else {
				ret = 0;
			}
		}
		return ret;
	}

	private ElementBoundary removeFoodBowl(ActionBoundary actionBoundary) {
		if (isFoodBowl(actionBoundary.getActionAttributes())) {
			ElementBoundary removed = removeElementBoundary(actionBoundary);
			if (removed != null)
				updateFeedingAreaStatus(removed, Operation.REMOVE);
			return removed;
		}
		return null;
	}

	private ElementBoundary removeWaterBowl(ActionBoundary actionBoundary) {
		if (isWaterBowl(actionBoundary.getActionAttributes())) {
			ElementBoundary removed = removeElementBoundary(actionBoundary);
			if (removed != null)
				updateFeedingAreaStatus(removed, Operation.REMOVE);
			return removed;
		}
		return null;
	}

	private ElementBoundary removeFeedingArea(ActionBoundary actionBoundary) {
		if (isFeedingArea(actionBoundary.getActionAttributes())) {
			return removeElementBoundary(actionBoundary);
		}
		return null;
	}

	private boolean isFoodBowl(Map<String, Object> actionAttributes) {
		// Check keys and values
		try {
			Boolean state = Boolean.parseBoolean(actionAttributes.get("state").toString());
			String animal = (String) actionAttributes.get("animal");
			String brand = (String) actionAttributes.get("brand");
			Integer weight = Integer.parseInt(actionAttributes.get("weight").toString());
			String lastFillDate = actionAttributes.get("lastFillDate").toString();
			if (state == null || animal == null || brand == null || weight == null
					|| !DatePattern.isDate(lastFillDate)) {
				throw new RuntimeException(actionAttributes.toString() + " does not describe a valid Food Bowl!");
			}
		} catch (RuntimeException e) {
			return false;
		}
		return true;
	}

	private boolean isWaterBowl(Map<String, Object> actionAttributes) {
		// Check keys and values
		try {
			Boolean state = Boolean.parseBoolean(actionAttributes.get("state").toString());
			String waterQuality = (String) actionAttributes.get("waterQuality");
			if (state == null || waterQuality == null)
				throw new RuntimeException(actionAttributes.toString() + " does not describe a valid Water Bowl!");
		} catch (RuntimeException e) {
			return false;
		}
		return true;
	}

	private boolean isFeedingArea(Map<String, Object> actionAttributes) {
		// Check keys and values
		try {
			Integer fullFoodBowl = Integer.parseInt(actionAttributes.get("fullFoodBowl").toString());
			Integer fullWaterBowl = Integer.parseInt(actionAttributes.get("fullWaterBowl").toString());
			if (fullFoodBowl == null || fullWaterBowl == null)
				throw new RuntimeException(actionAttributes.toString() + " does not describe a valid Feeding Area!");
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public List<ActionBoundary> getAllActions(String adminDomain, String adminEmail) {
		if (this.userService.isAdminValidation(adminDomain, adminEmail)) {
			return StreamSupport.stream(this.actionDao.findAll().spliterator(), false)
					.map(this.actionConverter::toBoundary).collect(Collectors.toList());
		} else {
			throw new RoleMismatchException(new UserId(adminDomain, adminEmail), "getAllActions");
		}
	}

	@Override
	@Transactional(readOnly = true)
	@MonitorPerformance
	public Collection<ActionBoundary> getAllActions(String adminDomain, String adminEmail, int size, int page) {
		if (this.userService.isAdminValidation(adminDomain, adminEmail)) {
			return this.actionDao
					.findAll(PageRequest.of(page, size, Direction.ASC, "actionId.actionDomain", "actionId.actionId"))
					.getContent().stream().map(this.actionConverter::toBoundary).collect(Collectors.toList());
		} else {
			throw new RoleMismatchException(new UserId(adminDomain, adminEmail), "getAllActions");
		}
	}

	@Override
	@Transactional
	@MonitorPerformance
	public void deleteAllActions(String adminDomain, String adminEmail) {
		if (this.userService.isAdminValidation(adminDomain, adminEmail)) {
			this.actionDao.deleteAll();
		} else {
			throw new RoleMismatchException(new UserId(adminDomain, adminEmail), "deleteAllActions");
		}
	}
}
