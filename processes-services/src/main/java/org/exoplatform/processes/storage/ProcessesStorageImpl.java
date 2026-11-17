package org.exoplatform.processes.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import io.meeds.common.ContainerTransactional;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.CreatorIdentityEntity;
import org.exoplatform.processes.model.IdentityEntity;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessReference;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkFlowLabel;
import org.exoplatform.processes.model.WorkStatus;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.domain.Priority;
import org.exoplatform.task.dto.LabelDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;
import java.util.Collection;
import java.util.Collections;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.exoplatform.task.dao.condition.PredicateCondition;
import org.exoplatform.task.domain.Task;
import org.exoplatform.processes.model.WorkDraftRef;
import org.exoplatform.processes.constant.ProcessesEvents;
import org.exoplatform.processes.model.ProcessDeletion;
import java.util.Objects;

public class ProcessesStorageImpl implements ProcessesStorage {

  private static final Log                 LOG                      = ExoLogger.getLogger(ProcessesStorageImpl.class);
  private static final String              WORK_DRAFT_ENTITY_TYPE   = "workdraft";
  private static final String              TASK_ENTITY_TYPE         = "task";
  private static final String              WORKFLOW_ENTITY_TYPE     = "workflow";
  private static final String[]            DEFAULT_PROCESS_STATUS   = { "Request", "RequestInProgress", "Validated", "Refused",
      "Canceled" };
  private static final String              PROCESS_FILES_NAME_SPACE = "processesApp";
  /** How many ids a query receives at most, below every database's parameter limit */
  private static final int                 ID_CHUNK_SIZE            = 1000;
  private final WorkFlowDAO                workFlowDAO;
  private final WorkDraftDAO               workDraftDAO;
  private final RequestDAO                 requestDAO;
  private final IdentityManager            identityManager;
  private final TaskService                taskService;
  private final ProjectService             projectService;
  private final StatusService              statusService;
  private final SpaceService               spaceService;
  private final ListenerService            listenerService;
  private final ProcessesAttachmentService processesAttachmentService;
  private final FileService                fileService;
  private final UserACL                    userACL;

  private final LabelService               labelService;

  /**
   * Builds the processes storage.
   *
   * @param workFlowDAO the processes DAO
   * @param workDraftDAO the request drafts DAO
   * @param requestDAO the request records DAO
   * @param taskService the Task add-on's task service
   * @param projectService the Task add-on's project service
   * @param statusService the Task add-on's status service
   * @param identityManager the identity manager
   * @param spaceService the space service
   * @param listenerService the listener service
   * @param processesAttachmentService the processes attachment service
   * @param fileService the file service
   * @param userACL the platform ACL
   * @param labelService the Task add-on's label service
   */
  @SuppressWarnings("java:S107")
  public ProcessesStorageImpl(WorkFlowDAO workFlowDAO,
                              WorkDraftDAO workDraftDAO,
                              RequestDAO requestDAO,
                              TaskService taskService,
                              ProjectService projectService,
                              StatusService statusService,
                              IdentityManager identityManager,
                              SpaceService spaceService,
                              ListenerService listenerService,
                              ProcessesAttachmentService processesAttachmentService,
                              FileService fileService,
                              UserACL userACL,
                              LabelService labelService) {
    this.workFlowDAO = workFlowDAO;
    this.workDraftDAO = workDraftDAO;
    this.requestDAO = requestDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
    this.processesAttachmentService = processesAttachmentService;
    this.fileService = fileService;
    this.userACL = userACL;
    this.labelService = labelService;
  }

  @Override
  public List<WorkFlow> findAllWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findAllWorkFlowsByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<WorkFlow> findEnabledWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findEnabledWorkFlowsByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<WorkFlow> findAllWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findAllWorkFlows(offset, limit));
  }

  @Override
  public List<WorkFlow> findEnabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findEnabledWorkFlows(offset, limit));
  }

  @Override
  public List<WorkFlow> findDisabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findDisabledWorkFlows(offset, limit));
  }

  /**
   * Reads a process with its labels that still exist and belong to its
   * project, and the permissions of the user on it.
   *
   * @param id the process id
   * @param userIdentityId the reading user's identity id, null for the
   *          platform's own read
   * @return the process, or null
   */
  @Override
  public WorkFlow getWorkFlowById(long id, Long userIdentityId) {
    Map.Entry<String, List<String>> userMemberships = getUserMemberships(userIdentityId);
    List<String> memberships = userMemberships.getValue();
    WorkFlowEntity workFlowEntity = workFlowDAO.find(id);
    WorkFlow workFlow = EntityMapper.fromEntity(workFlowEntity, memberships);
    if (workFlow != null) {
      workFlow.setLabels(readLabels(workFlowEntity));
    }
    return workFlow;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getRequestWorkFlowId(long taskId) {
    RequestEntity request = requestDAO.find(taskId);
    return request == null ? null : request.getWorkFlowId();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, Long> getRequestWorkFlowIds(Collection<Long> taskIds) {
    if (taskIds == null || taskIds.isEmpty()) {
      return Collections.emptyMap();
    }
    return requestDAO.findWorkFlowIdsByTaskIds(taskIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getRequestTaskIds(long workFlowId, Long afterTaskId, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("A page of request tasks needs a strictly positive limit");
    }
    TaskQuery taskQuery = requestsQuery(workFlowId);
    if (afterTaskId != null) {
      taskQuery.add(new PredicateCondition<Task>((cb, query, root) -> cb.greaterThan(root.<Long> get("id"), afterTaskId)));
    }
    taskQuery.setOrderBy(List.of(new OrderBy.ASC("id")));
    try {
      return taskService.findTasks(taskQuery, 0, limit).stream().map(TaskDto::getId).toList();
    } catch (Exception e) {
      throw new IllegalStateException("The request tasks of the process " + workFlowId + " could not be read", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<Long> getRequestProjectIds(long workFlowId) {
    List<Long> projectIds = taskService.selectTaskField(requestsQuery(workFlowId), "status.project.id");
    return projectIds == null ? Collections.emptySet() : new HashSet<>(projectIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countRequests(long workFlowId) {
    try {
      return taskService.countTasks(requestsQuery(workFlowId));
    } catch (Exception e) {
      throw new IllegalStateException("The request tasks of the process " + workFlowId + " could not be counted", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Predicate requestOf(CriteriaBuilder cb,
                             CommonAbstractCriteria query,
                             Expression<Long> taskId,
                             Collection<Long> workFlowIds) {
    return RequestDAO.requestOf(cb, query, taskId, workFlowIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, WorkDraftRef> getWorkDraftRefs(Collection<Long> draftIds) {
    if (draftIds == null || draftIds.isEmpty()) {
      return Collections.emptyMap();
    }
    return workDraftDAO.findDraftRefs(draftIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getWorkDraftIds(long workFlowId, Long afterDraftId, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("A page of drafts needs a strictly positive limit");
    }
    return workDraftDAO.findDraftIdsByWorkflowId(workFlowId, afterDraftId, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countWorkDrafts(long workFlowId) {
    return workDraftDAO.countDraftsByWorkflowId(workFlowId);
  }

  /**
   * @param workFlowId the process id
   * @return a task query restricted to the requests of the process
   */
  private TaskQuery requestsQuery(long workFlowId) {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.add(new PredicateCondition<Task>((cb,
                                                query,
                                                root) -> RequestDAO.requestOf(cb, query, root.<Long> get("id"), List.of(workFlowId))));
    return taskQuery;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, List<ProcessReference>> findProcessesOfProjects(List<Long> projectIds, long spaceId) {
    Map<Long, List<ProcessReference>> processes = new LinkedHashMap<>();
    if (projectIds == null) {
      return processes;
    }
    for (int start = 0; start < projectIds.size(); start += ID_CHUNK_SIZE) {
      List<Long> chunk = projectIds.subList(start, Math.min(start + ID_CHUNK_SIZE, projectIds.size()));
      for (Object[] row : workFlowDAO.findProcessesOfProjects(chunk, spaceId)) {
        processes.computeIfAbsent((Long) row[0], projectId -> new ArrayList<>())
                 .add(new ProcessReference((Long) row[1], (String) row[2]));
      }
    }
    return processes;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(since = "7.3.0.50", forRemoval = true)
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return EntityMapper.fromEntity(workFlowDAO.getWorkFlowByProjectId(projectId), null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkFlow saveWorkFlow(WorkFlow workFlow, long userId) throws IllegalArgumentException {
    if (workFlow == null) {
      throw new IllegalArgumentException("workflow argument is null");
    }
    Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    WorkFlowEntity workFlowEntity = EntityMapper.toEntity(workFlow);
    IllustrativeAttachment illustrativeAttachment = createIllustrativeImage(workFlow.getIllustrativeAttachment());
    if (illustrativeAttachment != null && !illustrativeAttachment.isToDelete()) {
      workFlowEntity.setIllustrationImageId(illustrativeAttachment.getId());
    } else if (illustrativeAttachment != null && illustrativeAttachment.isToDelete()) {
      workFlowEntity.setIllustrationImageId(null);
    }
    workFlowEntity.setModifiedDate(new Date());
    workFlowEntity.setModifierId(userId);
    if (workFlow.getId() == 0) {
      workFlowEntity.setId(null);
      workFlowEntity.setCreatedDate(new Date());
      workFlowEntity.setCreatorId(userId);
      if (workFlow.getProjectId() == 0) {
        // The Service creates a new process's project before it saves the
        // process, outside the transaction of the save
        throw new IllegalArgumentException("processes.project.required");
      }
      // The managing space's members access the process and its managers
      // manage it, whichever project of the space it feeds
      workFlowEntity.setParticipator(getSpaceParticipators(workFlow.getSpaceId()));
      workFlowEntity.setManager(getManagers(workFlow.getRequestsCreators()));
      workFlowEntity.setLabelIds(toLabelIds(workFlow.getLabels(), null));
      workFlowEntity = workFlowDAO.create(workFlowEntity);
    } else {
      // An update keeps the process's space and its participants, unless the
      // Service let it take a new space; it never rewrites the members of its
      // task project, which other processes may feed
      WorkFlowEntity storedWorkFlowEntity = workFlowDAO.find(workFlowEntity.getId());
      workFlowEntity.setLabelIds(toLabelIds(workFlow.getLabels(), storedWorkFlowEntity));
      if (storedWorkFlowEntity != null) {
        if (workFlowEntity.getSpaceId() == null) {
          // A client that does not state the space keeps the stored one
          workFlowEntity.setSpaceId(storedWorkFlowEntity.getSpaceId());
        }
        if (workFlowEntity.getSpaceId() != null && !workFlowEntity.getSpaceId().equals(storedWorkFlowEntity.getSpaceId())) {
          // A space the Service let a process take, its former one being
          // unknown, gives the process that space's participants
          workFlowEntity.setParticipator(getSpaceParticipators(String.valueOf(workFlowEntity.getSpaceId())));
        } else {
          workFlowEntity.setParticipator(storedWorkFlowEntity.getParticipator());
        }
      }
      workFlowEntity.setManager(getManagers(workFlow.getRequestsCreators()));
      workFlowEntity = workFlowDAO.update(workFlowEntity);
    }
    WorkFlow savedWorkFlow = EntityMapper.fromEntity(workFlowEntity, illustrativeAttachment, null);
    if (savedWorkFlow != null) {
      savedWorkFlow.setLabels(readLabels(workFlowEntity));
    }
    return savedWorkFlow;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void linkWorkFlowAttachments(WorkFlow savedWorkFlow, Attachment[] attachments, long userId) {
    processesAttachmentService.linkAttachmentsToEntity(attachments,
                                                       userId,
                                                       savedWorkFlow.getId(),
                                                       WORKFLOW_ENTITY_TYPE,
                                                       savedWorkFlow.getProjectId());
  }

  /**
   * Gives the label ids a save writes: those of the labels the Service
   * resolved, each checked to be a label of the process's project, else, when
   * the caller states none, the stored ones of an update, or none.
   *
   * @param labels the resolved labels, or null when not stated
   * @param storedWorkFlowEntity the stored process of an update, or null
   * @return the label ids
   */
  private Set<Long> toLabelIds(List<WorkFlowLabel> labels, WorkFlowEntity storedWorkFlowEntity) {
    if (labels == null) {
      return storedWorkFlowEntity == null || storedWorkFlowEntity.getLabelIds() == null ? new HashSet<>()
                                                                                         : new HashSet<>(storedWorkFlowEntity.getLabelIds());
    }
    Set<Long> labelIds = new HashSet<>();
    for (WorkFlowLabel label : labels) {
      if (label == null || label.getId() <= 0) {
        throw new IllegalArgumentException("processes.label.notInProject");
      }
      labelIds.add(label.getId());
    }
    return labelIds;
  }

  /**
   * Reads the labels of a stored process, by id order: a label deleted in
   * Tasks, or of another project than the process's, is left out.
   *
   * @param workFlowEntity the stored process
   * @return the labels
   */
  private List<WorkFlowLabel> readLabels(WorkFlowEntity workFlowEntity) {
    List<WorkFlowLabel> labels = new ArrayList<>();
    if (workFlowEntity == null || workFlowEntity.getLabelIds() == null) {
      return labels;
    }
    workFlowEntity.getLabelIds().stream().sorted().forEach(labelId -> {
      LabelDto label = findProjectLabel(labelId, workFlowEntity.getProjectId());
      if (label != null) {
        labels.add(new WorkFlowLabel(label.getId(), label.getName()));
      }
    });
    return labels;
  }

  /**
   * Reads a Task label when it still exists and belongs to a project.
   *
   * @param labelId the label id
   * @param projectId the project id
   * @return the label, or null
   */
  private LabelDto findProjectLabel(long labelId, Long projectId) {
    LabelDto label = labelService.getLabel(labelId);
    if (label == null || label.getProject() == null || projectId == null || label.getProject().getId() != projectId) {
      return null;
    }
    return label;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IllustrativeAttachment getIllustrationImageById(Long illustrationId) throws FileStorageException,
                                                                              ObjectNotFoundException,
                                                                              IOException {
    if (illustrationId == null) {
      return null;
    }
    FileItem file = fileService.getFile(illustrationId);
    if (file == null) {
      throw new ObjectNotFoundException("Illustration image not found");
    }
    FileInfo fileInfo = file.getFileInfo();
    return new IllustrativeAttachment(fileInfo.getId(),
                                      fileInfo.getName(),
                                      file.getAsStream(),
                                      fileInfo.getMimetype(),
                                      fileInfo.getSize(),
                                      fileInfo.getUpdatedDate().getTime());
  }

  private IllustrativeAttachment createIllustrativeImage(IllustrativeAttachment illustrativeAttachment) {
    if (illustrativeAttachment == null) {
      return null;
    }
    if (illustrativeAttachment.getFileName() == null) {
      illustrativeAttachment.setToDelete(true);
      return illustrativeAttachment;
    }
    if (illustrativeAttachment.getFileBody() == null) {
      return illustrativeAttachment;
    }
    FileItem fileItem;
    try {
      String data = illustrativeAttachment.getFileBody().split("base64,")[1];
      byte[] bytes = Base64.getDecoder().decode(data.getBytes(Charset.defaultCharset().name()));
      fileItem = new FileItem(illustrativeAttachment.getId(),
                              illustrativeAttachment.getFileName(),
                              illustrativeAttachment.getMimeType(),
                              PROCESS_FILES_NAME_SPACE,
                              illustrativeAttachment.getFileSize(),
                              new Date(),
                              null,
                              false,
                              new ByteArrayInputStream(bytes));
      if (illustrativeAttachment.getId() == null) {
        fileItem = fileService.writeFile(fileItem);
      } else {
        fileItem = fileService.updateFile(fileItem);
      }
      if (fileItem != null && fileItem.getFileInfo() != null) {
        FileInfo fileInfo = fileItem.getFileInfo();
        return new IllustrativeAttachment(fileInfo.getId(),
                                          fileInfo.getName(),
                                          fileInfo.getMimetype(),
                                          fileInfo.getSize(),
                                          fileInfo.getUpdatedDate().getTime());
      }
    } catch (Exception e) {
      LOG.error("Error while saving illustrative attachment", e);
    }
    return null;
  }

  @Override
  public List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {
    List<WorkFlow> workFlows = findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).collect(Collectors.toList());
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(projectsIds);
    if (workFilter.getStatus() != null) {
      taskQuery.setStatusName(workFilter.getStatus());
    }
    if (workFilter.getQuery() != null) {
      taskQuery.setKeyword(workFilter.getQuery());
    }
    if (workFilter.getCompleted() != null) {
      taskQuery.setCompleted(workFilter.getCompleted());
    }
    List<OrderBy> orderByList = new ArrayList<>();
    orderByList.add(new OrderBy("id", false));
    taskQuery.setOrderBy(orderByList);
    taskQuery.setCreatedBy(ProcessesUtils.getUserNameByIdentityId(identityManager, userIdentityId));
    List<TaskDto> tasks = taskService.findTasks(taskQuery, offset, limit);
    return (EntityMapper.tasksToWorkList(tasks));
  }

  @Override
  public Work getWorkById(long userIdentityId, long workId) {
    Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    TaskDto taskDto = null;
    TaskQuery taskQuery = new TaskQuery();
    try {
      taskQuery.setId(workId);
      taskQuery.setCreatedBy(identity.getRemoteId());
      List<TaskDto> list = taskService.findTasks(taskQuery, 0, 0);
      if (!list.isEmpty()) {
        taskDto = list.get(0);
      }
    } catch (EntityNotFoundException e) {
      throw new jakarta.persistence.EntityNotFoundException("work not found");
    } catch (Exception e) {
      LOG.error("Error while getting work");
    }
    return EntityMapper.taskToWork(taskDto);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorksByWorkflow(long projectId, boolean isCompleted) throws Exception {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(Arrays.asList(projectId));
    taskQuery.setCompleted(isCompleted);
    int tasksCount = taskService.countTasks(taskQuery);
    return tasksCount;
  }

  @Override
  public Work getWorkById(long id) {
    try {
      return EntityMapper.taskToWork(taskService.getTask(id));
    } catch (EntityNotFoundException e) {
      return null;
    }
  }

  /**
   * Creates the task of a new request in the request's project, in the
   * project's Request status.
   *
   * @param work the request, whose project id the Service set from its process
   * @param identity the requester
   * @return the created task
   * @throws IllegalArgumentException when the project does not exist
   */
  private TaskDto createWorkTask(Work work, Identity identity) {
    TaskDto taskDto;
    try {
      projectService.getProject(work.getProjectId());
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException("Task's project not found");
    }
    // The Service names every request by the fixed rule (RequestTitle)
    taskDto = EntityMapper.workToTask(work);
    taskDto.setStatus(getRequestStatus(work.getProjectId()));
    taskDto.setCreatedBy(identity.getRemoteId());
    taskDto.setCreatedTime(new Date());
    taskDto.setPriority(Priority.NONE);
    taskDto = taskService.createTask(taskDto);
    return taskDto;
  }

  /**
   * Reads the status a new request starts in: the project's status named
   * Request exactly, the one of lowest id when several are, else, when a
   * manager deleted it in Tasks, the project's default status. A shared project
   * starts with its own columns, so its default status is not a request
   * status.
   *
   * @param projectId the request's project id
   * @return the status
   */
  private StatusDto getRequestStatus(long projectId) {
    StatusDto status = findStatusByName(projectId, DEFAULT_PROCESS_STATUS[0]);
    if (status == null) {
      LOG.warn("The project {} has no status named {}, the request is created in its default status",
               projectId,
               DEFAULT_PROCESS_STATUS[0]);
      return statusService.getDefaultStatus(projectId);
    }
    return status;
  }

  /**
   * Finds a status of a project by its exact name, the one of lowest id when
   * several have it, rather than the first in the board's column order, which
   * managers change in Tasks.
   *
   * @param projectId the project id
   * @param name the status name
   * @return the status, or null when none has that name
   */
  private StatusDto findStatusByName(long projectId, String name) {
    List<StatusDto> statuses = statusService.getStatuses(projectId);
    if (statuses == null) {
      return null;
    }
    return statuses.stream()
                   .filter(statusDto -> name.equals(statusDto.getName()))
                   .min(Comparator.comparingLong(StatusDto::getId))
                   .orElse(null);
  }

  private TaskDto updateWorkTask(Work work) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(work.getId());
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException("Task not found");
    }
    taskDto.setDescription(work.getDescription());
    taskDto.setTitle(work.getTitle());
    taskDto.setCompleted(work.isCompleted());
    long projectId = work.getProjectId();
    List<StatusDto> statuses = statusService.getStatuses(projectId);
    StatusDto status = statuses.stream().filter(statusDto -> work.getStatus().equals(statusDto.getName())).findAny().orElse(null);
    if (status != null) {
      taskDto.setStatus(status);
    }
    taskDto = taskService.updateTask(taskDto);
    if (taskDto.isCompleted() && taskDto.getStatus().getName().equals(DEFAULT_PROCESS_STATUS[4])) {
      ProjectDto projectDto = taskDto.getStatus().getProject();
      ProcessesUtils.broadcast(listenerService, "exo.process.request.canceled", taskDto, projectDto);
    }
    return taskDto;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work saveWork(Work work, long userId) throws IllegalArgumentException {
    if (work == null) {
      throw new IllegalArgumentException("work argument is null");
    }
    Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    if (work.getId() == 0) {
      TaskDto taskDto = createRequestTask(work, identity, userId);
      return completeRequest(taskDto, work, userId);
    } else {
      TaskDto taskDto = updateWorkTask(work);
      return EntityMapper.taskToWork(taskDto);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createRequest(Work work, long userId) {
    Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    TaskDto taskDto = createRequestTask(work, identity, userId);
    Work request = EntityMapper.taskToWork(taskDto);
    request.setCreatorId(userId);
    request.setWorkFlowId(work.getWorkFlowId());
    return request;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeRequest(long taskId) {
    try {
      taskService.removeTask(taskId);
    } catch (Exception e) {
      LOG.warn("The task {} of a request whose values were refused could not be removed", taskId, e);
    }
    try {
      RequestEntity request = requestDAO.find(taskId);
      if (request != null) {
        requestDAO.delete(request);
      }
    } catch (RuntimeException e) {
      LOG.warn("The record of the request {} whose values were refused could not be removed", taskId, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work completeRequest(Work request, Work work, long userId) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(request.getId());
    } catch (EntityNotFoundException e) {
      throw new IllegalStateException("The task of the request " + request.getId() + " is gone", e);
    }
    return completeRequest(taskDto, work, userId);
  }

  /**
   * Creates a new request's task, its record and its labels.
   *
   * @param work the new request
   * @param identity the requester's identity
   * @param userId the requester's identity id
   * @return the created task
   */
  private TaskDto createRequestTask(Work work, Identity identity, long userId) {
    TaskDto taskDto = createWorkTask(work, identity);
    recordRequest(taskDto, work.getWorkFlowId(), userId);
    applyLabels(taskDto, work.getWorkFlowId());
    return taskDto;
  }

  /**
   * Ends a new request's creation on its task: moves its draft's attachments,
   * deletes its draft and announces it, then announces the request.
   *
   * @param taskDto the request's task
   * @param work the new request as submitted
   * @param userId the requester's identity id
   * @return the created request
   */
  private Work completeRequest(TaskDto taskDto, Work work, long userId) {
    ProjectDto projectDto = taskDto.getStatus().getProject();
    if (work.getDraftId() != null) {
      processesAttachmentService.moveAttachmentsToEntity(work.getAttachments(),
                                                         userId,
                                                         work.getDraftId(),
                                                         WORK_DRAFT_ENTITY_TYPE,
                                                         taskDto.getId(),
                                                         TASK_ENTITY_TYPE,
                                                         projectDto.getId());
      deleteWorkDraftById(work.getDraftId());
      ProcessesUtils.broadcast(listenerService, ProcessesEvents.REQUEST_DRAFT_DELETED, work.getDraftId(), work.getWorkFlowId());
    }
    Work newWork = EntityMapper.taskToWork(taskDto);
    newWork.setCreatorId(userId);
    newWork.setWorkFlowId(work.getWorkFlowId());
    ProcessesUtils.broadcast(listenerService, "exo.process.request.created", newWork, projectDto);
    return newWork;
  }

  /**
   * Applies the labels of a request's process to its task: each one that
   * still exists and belongs to the task's project, on its own. A label that
   * can't be applied is logged and does not fail the request, which Task has
   * already created.
   *
   * @param taskDto the request's task
   * @param workFlowId the request's process id
   */
  private void applyLabels(TaskDto taskDto, long workFlowId) {
    WorkFlowEntity workFlowEntity = workFlowDAO.find(workFlowId);
    if (workFlowEntity == null || workFlowEntity.getLabelIds() == null || taskDto.getStatus() == null
        || taskDto.getStatus().getProject() == null) {
      return;
    }
    long projectId = taskDto.getStatus().getProject().getId();
    for (Long labelId : workFlowEntity.getLabelIds().stream().sorted().toList()) {
      try {
        if (findProjectLabel(labelId, projectId) != null) {
          labelService.addTaskToLabel(taskDto, labelId);
        }
      } catch (Exception e) {
        LOG.warn("The label {} of the process {} could not be applied to the request {}", labelId, workFlowId, taskDto.getId(), e);
      }
    }
  }

  /**
   * Records a created task as a request of its process. Task commits the task
   * on its own, so no transaction covers both: when the record cannot be
   * saved, for instance because the process was deleted meanwhile, the task
   * is removed, so no task stays in a shared project without its record.
   *
   * @param taskDto the created task
   * @param workFlowId the request's process id
   * @param requesterIdentityId the requester's identity id
   * @throws IllegalStateException when the record cannot be saved, the task
   *           being removed
   */
  private void recordRequest(TaskDto taskDto, long workFlowId, long requesterIdentityId) {
    try {
      requestDAO.create(new RequestEntity(taskDto.getId(), workFlowId, requesterIdentityId));
    } catch (RuntimeException e) {
      try {
        taskService.removeTask(taskDto.getId());
      } catch (Exception removalError) {
        LOG.warn("The task {} of a request of the process {} could not be removed after its record failed",
                 taskDto.getId(),
                 workFlowId,
                 removalError);
      }
      throw new IllegalStateException("The request of the process " + workFlowId + " could not be recorded", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkFlow createProject(WorkFlow workFlow) {
    Space processSpace = spaceService.getSpaceById(workFlow.getSpaceId());
    if (processSpace == null) {
      throw new IllegalArgumentException("Space of processes not exist");
    }

    List<String> memberships = UserUtil.getSpaceMemberships(processSpace.getGroupId());
    Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
    ProjectDto project =
                       ProjectUtil.newProjectInstanceDto(workFlow.getTitle(), workFlow.getDescription(), managers, participators);
    project = projectService.createProject(project);
    for (String statusName : DEFAULT_PROCESS_STATUS) {
      statusService.createStatus(project, statusName);
    }
    workFlow.setProjectId(project.getId());
    workFlow.setProjectId(project.getId());
    participators.addAll(managers);
    workFlow.setManager(managers);
    workFlow.setParticipator(participators);
    return workFlow;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @ContainerTransactional(atomic = true)
  public ProcessDeletion deleteWorkflowById(Long workflowId) throws jakarta.persistence.EntityNotFoundException {
    return deleteWorkflowRows(workflowId);
  }

  /**
   * Deletes a process's own rows: its drafts, its request records, then the
   * process. Its task project and the project's tasks are never deleted: the
   * project may be fed by other processes, and its tasks stay as plain tasks.
   * {@link #deleteWorkflowById} runs it in one transaction, so a failure leaves
   * the process and its request records as they were.
   *
   * @param workflowId the process id
   * @return the task ids the process's request records held and the ids of
   *         its drafts, read in the same transaction as their deletion
   * @throws jakarta.persistence.EntityNotFoundException when the process does
   *           not exist
   */
  ProcessDeletion deleteWorkflowRows(Long workflowId) {
    WorkFlowEntity workFlowEntity = this.workFlowDAO.find(workflowId);
    if (workFlowEntity == null) {
      throw new jakarta.persistence.EntityNotFoundException("Workflow not found");
    }
    List<WorkEntity> drafts = this.workDraftDAO.getDraftsByWorkflowId(workflowId);
    if (!drafts.isEmpty()) {
      workDraftDAO.deleteAll(drafts);
    }
    List<Long> requestTaskIds = requestDAO.findTaskIdsByWorkFlowId(workflowId);
    requestDAO.deleteByWorkFlowId(workflowId);
    this.workFlowDAO.delete(workFlowEntity);
    return new ProcessDeletion(requestTaskIds, drafts.stream().map(WorkEntity::getId).filter(Objects::nonNull).toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkById(Long workId) {
    try {
      TaskDto taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskService.removeTask(workId);
        ProjectDto projectDto = taskDto.getStatus().getProject();
        ProcessesUtils.broadcast(listenerService, "exo.process.request.removed", taskDto, projectDto);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Work not found", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkCompleted(Long workId, boolean completed) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskDto.setCompleted(completed);
        taskDto = taskService.updateTask(taskDto);
      }
    } catch (EntityNotFoundException e) {
      throw new jakarta.persistence.EntityNotFoundException("work not found");
    }
    return EntityMapper.taskToWork(taskDto);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Work> findAllWorkDraftsByUser(WorkFilter workFilter, int offset, int limit, long userIdentityId) {
    return EntityMapper.fromWorkEntities(workDraftDAO.findAllWorkDraftsByUser(workFilter, userIdentityId, offset, limit));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work saveWorkDraft(Work work, long userId) {
    Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    WorkEntity workEntity = EntityMapper.toEntity(work);
    workEntity.setModifiedDate(new Date());
    if (work.getId() == 0) {
      workEntity.setId(null);
      workEntity.setCreatedDate(new Date());
      workEntity.setCreatorId(userId);
      workEntity = workDraftDAO.create(workEntity);
      processesAttachmentService.copyAttachmentsToEntity(userId,
                                                         work.getWorkFlow().getId(),
                                                         WORKFLOW_ENTITY_TYPE,
                                                         workEntity.getId(),
                                                         WORK_DRAFT_ENTITY_TYPE,
                                                         work.getWorkFlow().getProjectId());
    } else {
      workEntity = workDraftDAO.update(workEntity);
    }

    return EntityMapper.fromEntity(workEntity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work getWorkDraftyId(long id) {
    return EntityMapper.fromEntity(workDraftDAO.find(id));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkDraftById(long id) throws jakarta.persistence.EntityNotFoundException {
    WorkEntity workEntity = workDraftDAO.find(id);
    if (workEntity == null) {
      throw new jakarta.persistence.EntityNotFoundException("Work Draft not found");
    }
    workDraftDAO.delete(workEntity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkStatus> getAvailableWorkStatuses() {
    List<WorkStatus> statuses = new ArrayList<>();
    List<WorkFlow> workFlows = findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).collect(Collectors.toList());
    projectsIds.forEach(projectId -> statuses.addAll(EntityMapper.toWorkStatuses(statusService.getStatuses(projectId))));
    statuses.sort(Comparator.comparing(WorkStatus::getRank));
    return statuses;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkFlow> findWorkFlows(ProcessesFilter processesFilter, long userIdentityId, int offset, int limit) {
    Map.Entry<String, List<String>> userMemberships = getUserMemberships(userIdentityId);
    String userName = userMemberships.getKey();
    List<String> memberships = userMemberships.getValue();
    List<String> listedMemberships = memberships;
    if (memberships != null && processesFilter != null && Boolean.TRUE.equals(processesFilter.getManager())) {
      // The processes a user manages are those whose managing space the user
      // administers, not every one whose space the user is a member of
      listedMemberships = memberships.stream()
                                     .filter(membership -> membership.startsWith(ProcessesUtils.MANAGER_MEMBERSHIP_TYPE + ":"))
                                     .toList();
      if (listedMemberships.isEmpty()) {
        return new ArrayList<>();
      }
    }
    List<WorkFlowEntity> workFlowEntities = workFlowDAO.findWorkFlows(processesFilter, listedMemberships, offset, limit);
    List<WorkFlow> workFlows = new ArrayList<>();
    List<String> finalMemberships = memberships;
    workFlowEntities.forEach(workflowEntity -> {
      IllustrativeAttachment illustrativeAttachment = null;
      try {
        illustrativeAttachment = getIllustrationImageById(workflowEntity.getIllustrationImageId());
        if (illustrativeAttachment != null) {
          illustrativeAttachment.setFileInputStream(null);
        }
      } catch (Exception e) {
        LOG.error("Error while getting workflow illustration image", e);
      }
      workFlows.add(EntityMapper.fromEntity(workflowEntity, illustrativeAttachment, finalMemberships));
    });
    String finalUserName = userName;
    workFlows.forEach(workflow -> {
      if (workflow != null) {
        Space space = ProcessesUtils.getProjectParentSpace(workflow.getProjectId());
        workflow.setCanShowPending(space != null && (spaceService.isSuperManager(finalUserName) || spaceService.isMember(space, finalUserName)));
      }
    });
    return workFlows;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorkFlows(ProcessesFilter processesFilter) {
    return workFlowDAO.countWorkFlows(processesFilter);
  }

  private Map.Entry<String, List<String>> getUserMemberships(Long userIdentityId) {
    if (userIdentityId == null || userIdentityId <= 0) {
      return Map.entry("", new ArrayList<>());
    }
    Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null) {
      return Map.entry("", new ArrayList<>());
    }
    String userName = identity.getRemoteId();
    org.exoplatform.services.security.Identity aclIdentity = userACL.getUserIdentity(userName);
    if (ProcessesUtils.isProcessesAdministrator(userACL, aclIdentity)) {
      return new AbstractMap.SimpleEntry<>(userName, null);
    }
    List<String> memberships = new ArrayList<>();
    memberships.add(userName);
    if (CollectionUtils.isNotEmpty(aclIdentity.getMemberships())) {
      memberships.addAll(aclIdentity.getMemberships()
                                    .stream()
                                    .flatMap(m -> toQueriedMemberships(m.getMembershipType(), m.getGroup()))
                                    .distinct()
                                    .toList());
    }
    return Map.entry(userName, memberships);
  }

  /**
   * A process stores the memberships of its managing space as 'manager:' and
   * 'member:' entries, which the lists match as strings: a '*' membership,
   * which the permissions count as both, is queried as both too
   */
  private Stream<String> toQueriedMemberships(String membershipType, String group) {
    String membership = membershipType + ":" + group;
    if (!MembershipEntry.ANY_TYPE.equals(membershipType)) {
      return Stream.of(membership);
    }
    return Stream.of(membership,
                     ProcessesUtils.MANAGER_MEMBERSHIP_TYPE + ":" + group,
                     ProcessesUtils.MEMBER_MEMBERSHIP_TYPE + ":" + group);
  }
  
  /**
   * Builds the participator entries of a process from its managing space: the
   * space's manager and member memberships.
   *
   * @param spaceId the managing space id
   * @return the entries
   * @throws IllegalArgumentException when the space does not exist
   */
  private Set<String> getSpaceParticipators(String spaceId) {
    Space space = StringUtils.isBlank(spaceId) ? null : spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new IllegalArgumentException("Space of processes not exist");
    }
    return new HashSet<>(UserUtil.getSpaceMemberships(space.getGroupId()));
  }

  private Set<String> getManagers(List<CreatorIdentityEntity> requestsCreators) {
    Set<String> managers = new HashSet<>();
    for (CreatorIdentityEntity creatorIdentityEntity : requestsCreators) {
      IdentityEntity creatorIdentity = creatorIdentityEntity.getIdentity();
      if ("space".equals(creatorIdentity.getProviderId())) {
        Space space = spaceService.getSpaceByPrettyName(creatorIdentity.getRemoteId());
        if (space != null) {
          managers.add(space.getGroupId());
        }
      } else {
        managers.add(creatorIdentity.getRemoteId());
      }
    }
    return managers;
  }
}
