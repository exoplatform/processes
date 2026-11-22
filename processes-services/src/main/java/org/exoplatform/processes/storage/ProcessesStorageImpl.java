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
import org.exoplatform.task.dto.CommentsSummaryDto;
import org.exoplatform.task.dto.LabelDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.CommentService;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;
import java.util.Collection;
import java.util.Collections;
import jakarta.persistence.NoResultException;
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
import org.exoplatform.processes.model.ProjectMoveState;
import org.exoplatform.processes.constant.TaskMoveResult;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;

public class ProcessesStorageImpl implements ProcessesStorage {

  /** The refusal of a request in a task project that has no column. */
  public static final String               NO_STATUS                = "processes.project.noStatus";

  private static final Log                 LOG                      = ExoLogger.getLogger(ProcessesStorageImpl.class);
  private static final String              WORK_DRAFT_ENTITY_TYPE   = "workdraft";
  private static final String              TASK_ENTITY_TYPE         = "task";
  private static final String              WORKFLOW_ENTITY_TYPE     = "workflow";
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

  private final CommentService             commentService;

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
   * @param commentService the Task add-on's comment service
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
                              LabelService labelService,
                              CommentService commentService) {
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
    this.commentService = commentService;
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
      workFlow.setRequesterFormIds(readRequesterFormIds(workFlowEntity));
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
  public long countRequests(long workFlowId, boolean completed) {
    TaskQuery taskQuery = requestsQuery(workFlowId);
    taskQuery.setCompleted(completed);
    try {
      return taskService.countTasks(taskQuery);
    } catch (Exception e) {
      throw new IllegalStateException("The request tasks of the process " + workFlowId + " could not be counted", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countRequests(Collection<Long> workFlowIds, Boolean completed) {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.add(new PredicateCondition<Task>((cb, query, root) -> RequestDAO.requestOf(cb, query, root.<Long> get("id"), workFlowIds)));
    if (completed != null) {
      taskQuery.setCompleted(completed);
    }
    try {
      return taskService.countTasks(taskQuery);
    } catch (Exception e) {
      throw new IllegalStateException("The request tasks of the processes " + workFlowIds + " could not be counted", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getWorkFlowIdsByProjectId(long projectId) {
    return workFlowDAO.findWorkFlowIdsByProjectId(projectId);
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
      workFlowEntity.setRequesterFormIds(toRequesterFormIds(workFlow.getRequesterFormIds(), null));
      workFlowEntity = workFlowDAO.create(workFlowEntity);
    } else {
      // An update keeps the process's space and its participants, unless the
      // Service let it take a new space; it never rewrites the members of its
      // task project, which other processes may feed
      WorkFlowEntity storedWorkFlowEntity = workFlowDAO.find(workFlowEntity.getId());
      workFlowEntity.setLabelIds(toLabelIds(workFlow.getLabels(), storedWorkFlowEntity));
      workFlowEntity.setRequesterFormIds(toRequesterFormIds(workFlow.getRequesterFormIds(), storedWorkFlowEntity));
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
      workFlowEntity.setManager(getUpdatedManagers(workFlow.getRequestsCreators(), storedWorkFlowEntity));
      workFlowEntity = workFlowDAO.update(workFlowEntity);
    }
    WorkFlow savedWorkFlow = EntityMapper.fromEntity(workFlowEntity, illustrativeAttachment, null);
    if (savedWorkFlow != null) {
      savedWorkFlow.setLabels(readLabels(workFlowEntity));
      savedWorkFlow.setRequesterFormIds(readRequesterFormIds(workFlowEntity));
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
   * {@inheritDoc}
   */
  @Override
  public List<Long> getRequesterFormIds(long workFlowId) {
    return readRequesterFormIds(workFlowDAO.find(workFlowId));
  }

  /**
   * Gives the requester form ids a save writes: those the Service checked,
   * else, when the caller states none, the stored ones of an update, or none.
   *
   * @param formIds the checked form ids, or null when not stated
   * @param storedWorkFlowEntity the stored process of an update, or null
   * @return the form ids
   */
  private Set<Long> toRequesterFormIds(List<Long> formIds, WorkFlowEntity storedWorkFlowEntity) {
    if (formIds == null) {
      return storedWorkFlowEntity == null || storedWorkFlowEntity.getRequesterFormIds() == null ? new HashSet<>()
                                                                                                 : new HashSet<>(storedWorkFlowEntity.getRequesterFormIds());
    }
    return new HashSet<>(formIds);
  }

  /**
   * @param workFlowEntity the stored process, or null
   * @return the ids of the forms its requesters fill, in id order
   */
  private static List<Long> readRequesterFormIds(WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null || workFlowEntity.getRequesterFormIds() == null) {
      return new ArrayList<>();
    }
    return workFlowEntity.getRequesterFormIds().stream().sorted().toList();
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

  /**
   * {@inheritDoc} Only the tasks a process created are listed, whatever
   * project they are in now: a task created in Tasks, in a project processes
   * feed, is not a request. Each request names the process it was created
   * for, from its request record, whichever processes share its project.
   */
  @Override
  public List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {
    TaskQuery taskQuery = toWorksQuery(userIdentityId, workFilter);
    List<OrderBy> orderByList = new ArrayList<>();
    orderByList.add(new OrderBy("id", false));
    taskQuery.setOrderBy(orderByList);
    List<TaskDto> tasks = taskService.findTasks(taskQuery, offset, limit);
    List<Work> works = withRequestWorkFlows(EntityMapper.tasksToWorkList(tasks));
    if (Boolean.TRUE.equals(workFilter.getWithCommentsCount())) {
      setCommentsCounts(works);
    }
    return works;
  }

  /**
   * Sets each request's comments count, replies included, as the request
   * drawer counts them, read for the whole page at once.
   *
   * @param works the requests of a page
   */
  private void setCommentsCounts(List<Work> works) {
    List<Long> taskIds = works.stream().filter(Objects::nonNull).map(Work::getId).toList();
    if (taskIds.isEmpty()) {
      return;
    }
    Map<Long, CommentsSummaryDto> summaries = commentService.getCommentsSummaries(taskIds);
    works.stream().filter(Objects::nonNull).forEach(work -> {
      CommentsSummaryDto summary = summaries == null ? null : summaries.get(work.getId());
      // a task without comments is absent from the summaries
      work.setCommentsCount(summary == null ? 0 : Math.toIntExact(summary.getCount()));
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorks(long userIdentityId, WorkFilter workFilter) throws Exception {
    return taskService.countTasks(toWorksQuery(userIdentityId, workFilter));
  }

  /**
   * Builds the query of the request tasks a user created: the tasks a request
   * record names, by that creator, with the filter's status, text,
   * completion and archive flag (the request record's, null for both).
   *
   * @param userIdentityId the creator's identity id
   * @param workFilter the filter
   * @return the task query, unordered
   */
  private TaskQuery toWorksQuery(long userIdentityId, WorkFilter workFilter) {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.add(new PredicateCondition<Task>((cb,
                                                query,
                                                root) -> RequestDAO.isRequest(cb,
                                                                              query,
                                                                              root.<Long> get("id"),
                                                                              workFilter.getArchived())));
    if (workFilter.getStatus() != null) {
      taskQuery.setStatusName(workFilter.getStatus());
    }
    if (workFilter.getQuery() != null) {
      taskQuery.setKeyword(workFilter.getQuery());
    }
    if (workFilter.getCompleted() != null) {
      taskQuery.setCompleted(workFilter.getCompleted());
    }
    taskQuery.setCreatedBy(ProcessesUtils.getUserNameByIdentityId(identityManager, userIdentityId));
    return taskQuery;
  }

  /**
   * Sets each request of a page read from its tasks to the process its
   * request record names, the records read in one query: a task alone tells
   * only its project, which several processes may share. A task with no
   * record keeps no process.
   *
   * @param works the requests read from their tasks
   * @return the same requests
   */
  private List<Work> withRequestWorkFlows(List<Work> works) {
    List<Long> taskIds = works.stream().filter(Objects::nonNull).map(Work::getId).toList();
    if (taskIds.isEmpty()) {
      return works;
    }
    Map<Long, RequestEntity> requests = requestDAO.findByTaskIds(taskIds);
    works.stream().filter(Objects::nonNull).forEach(work -> withRequestRecord(work, requests.get(work.getId())));
    return works;
  }

  /**
   * Sets a request read from its task to what its request record holds: the
   * process it was created for, unless the request already names one, and
   * whether its requester cancelled or archived it.
   *
   * @param work the request read from its task
   * @param request its request record, or null when the task has none
   */
  private static void withRequestRecord(Work work, RequestEntity request) {
    if (request == null) {
      return;
    }
    if (work.getWorkFlowId() <= 0 && request.getWorkFlowId() != null) {
      work.setWorkFlowId(request.getWorkFlowId());
    }
    work.setCanceled(request.isCanceledRequest());
    work.setArchived(request.isArchivedRequest());
  }

  /**
   * {@inheritDoc} The request names the process it was created for, from its
   * request record, whichever processes share its project.
   */
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
    return withRequestWorkFlow(EntityMapper.taskToWork(taskDto));
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

  /**
   * {@inheritDoc} The request names the process it was created for, from its
   * request record, whichever processes share its project.
   */
  @Override
  public Work getWorkById(long id) {
    try {
      return withRequestWorkFlow(EntityMapper.taskToWork(taskService.getTask(id)));
    } catch (EntityNotFoundException e) {
      return null;
    }
  }

  /**
   * Sets a request read from its task to the process its request record
   * names: a task alone tells only its project, which several processes may
   * share. A task with no record keeps no process.
   *
   * @param work the request read from its task, or null
   * @return the same request
   */
  private Work withRequestWorkFlow(Work work) {
    if (work != null) {
      withRequestRecord(work, requestDAO.find(work.getId()));
    }
    return work;
  }

  /**
   * Creates the task of a new request in the request's project, in the
   * project's first column.
   *
   * @param work the request, whose project id the Service set from its process
   * @param identity the requester
   * @return the created task
   * @throws IllegalArgumentException when the project does not exist, or
   *           processes.project.noStatus when it has no column
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
   * Reads the status a new request starts in: the project's first column, the
   * one of lowest rank, whatever its name, so a project's columns may have any
   * names and any number.
   *
   * @param projectId the request's project id
   * @return the status
   * @throws IllegalArgumentException processes.project.noStatus when the
   *           project has no column
   */
  private StatusDto getRequestStatus(long projectId) {
    StatusDto status;
    try {
      status = statusService.getDefaultStatus(projectId);
    } catch (NoResultException e) {
      // Task's lowest-rank read throws on a project with no column
      status = null;
    }
    if (status == null) {
      throw new IllegalArgumentException(NO_STATUS);
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

  /**
   * Updates a request's task: its title, description, completion and status.
   * A task not in its process's stored project, which a move has not reached
   * yet, is moved there first, so that a request update racing the move never
   * sends its task back to the former project; the status is then the stored
   * project's status of that name.
   *
   * @param work the request, its fields checked by the service
   * @return the updated task
   */
  private TaskDto updateWorkTask(Work work) {
    long projectId = moveToStoredProject(work.getId(), work.getProjectId());
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(work.getId());
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException("Task not found");
    }
    taskDto.setDescription(work.getDescription());
    taskDto.setTitle(work.getTitle());
    taskDto.setCompleted(work.isCompleted());
    StatusDto status = work.getStatus() == null ? null : findStatusByName(projectId, work.getStatus());
    if (status != null) {
      taskDto.setStatus(status);
    }
    return taskService.updateTask(taskDto);
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
      return withRequestWorkFlow(EntityMapper.taskToWork(taskDto));
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
      // A new request's state is known: neither cancelled nor archived. Only
      // the records of older requests have none, which the state upgrade fills
      requestDAO.create(new RequestEntity(taskDto.getId(), workFlowId, requesterIdentityId, Boolean.FALSE, Boolean.FALSE));
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
    // Task's own initial columns: processes reads none of their names
    statusService.createInitialStatuses(project);
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
   * {@inheritDoc} The request returned names the process it was created for,
   * from its request record.
   */
  @Override
  public Work updateWorkCompleted(Long workId, boolean completed) {
    TaskDto taskDto;
    moveToStoredProject(workId, 0);
    try {
      taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskDto.setCompleted(completed);
        taskDto = taskService.updateTask(taskDto);
      }
    } catch (EntityNotFoundException e) {
      throw new jakarta.persistence.EntityNotFoundException("work not found");
    }
    return withRequestWorkFlow(EntityMapper.taskToWork(taskDto));
  }

  /**
   * {@inheritDoc} The record's flags are written first, in their own
   * transaction, then the task's completion through Task, which commits it and
   * broadcasts its update after that commit, as for any task update: no
   * transaction of processes wraps it, so Task's listeners never read the task
   * before its write is committed. Between the two commits a reader sees the
   * new flags on an open task, which reads pending
   * ({@link org.exoplatform.processes.util.RequestState}). When the task's
   * write fails, the record's former flags are written back and the task's
   * failure is thrown: a cancel left without its completion would read
   * cancelled at the next completion, and an upgrade step would not be read
   * again by the next run. The new flags stay on the open task only when that
   * write-back fails too, its failure logged and attached to the thrown one,
   * or when the process stops between the two writes. The request then reads
   * pending; a cancel retried by the requester completes it as cancelled, a
   * manager who completes it, in Tasks or in processes, sees it read
   * cancelled rather than decided, and only a reopen of its task clears the
   * flags.
   */
  @Override
  public Work saveRequestState(long workId, Boolean canceled, Boolean archived, Boolean completed) {
    RequestEntity request = requestDAO.find(workId);
    if (request == null) {
      throw new jakarta.persistence.EntityNotFoundException("request not found");
    }
    Boolean formerCanceled = request.getCanceled();
    Boolean formerArchived = request.getArchived();
    boolean flagsWritten = writeRequestFlags(request, canceled, archived);
    if (completed == null) {
      return getWorkById(workId);
    }
    try {
      return updateWorkCompleted(workId, completed);
    } catch (RuntimeException e) {
      if (flagsWritten) {
        restoreRequestFlags(request, formerCanceled, formerArchived, e);
      }
      throw e;
    }
  }

  /**
   * Writes a request record's former flags back after its task's write
   * failed. A write-back that fails too is logged and added to the task's
   * failure as suppressed, which stays the one thrown: the record then keeps
   * the new flags, which read pending as long as the task is open.
   *
   * @param request the request record
   * @param formerCanceled the cancelled flag before the write
   * @param formerArchived the archived flag before the write
   * @param failure the task's write failure
   */
  private void restoreRequestFlags(RequestEntity request, Boolean formerCanceled, Boolean formerArchived, RuntimeException failure) {
    request.setCanceled(formerCanceled);
    request.setArchived(formerArchived);
    try {
      requestDAO.update(request);
    } catch (RuntimeException restoreFailure) {
      LOG.warn("The state of the request {} could not be written back after its task's write failed", request.getTaskId(), restoreFailure);
      failure.addSuppressed(restoreFailure);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void broadcastRequestCanceled(long workId) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(workId);
    } catch (EntityNotFoundException e) {
      LOG.debug("The cancelled request {} has no task left to notify", workId, e);
      return;
    }
    ProjectDto projectDto = taskDto == null || taskDto.getStatus() == null ? null : taskDto.getStatus().getProject();
    if (projectDto == null) {
      LOG.debug("The cancelled request {} is in no project: no notification", workId);
      return;
    }
    ProcessesUtils.broadcast(listenerService, ProcessesEvents.REQUEST_CANCELED, taskDto, projectDto);
  }

  /**
   * Writes the given flags on a request record, keeping a flag given as null.
   *
   * @param request the request record
   * @param canceled the cancelled flag to write, null to keep it
   * @param archived the archived flag to write, null to keep it
   * @return true when the record was written
   */
  private boolean writeRequestFlags(RequestEntity request, Boolean canceled, Boolean archived) {
    if (canceled == null && archived == null) {
      return false;
    }
    if (canceled != null) {
      request.setCanceled(canceled);
    }
    if (archived != null) {
      request.setArchived(archived);
    }
    requestDAO.update(request);
    return true;
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
    WorkEntity workEntity = saveWorkDraftEntity(work, userId);
    if (work.getId() == 0) {
      copyProcessAttachmentsToDraft(workEntity.getId(), work.getWorkFlow(), userId);
    }
    return EntityMapper.fromEntity(workEntity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work saveWorkDraftRow(Work work, long userId) {
    return EntityMapper.fromEntity(saveWorkDraftEntity(work, userId));
  }

  /**
   * Saves a request draft's row, the server setting its draft state.
   *
   * @param work the draft
   * @param userId the owner's identity id
   * @return the saved row
   */
  private WorkEntity saveWorkDraftEntity(Work work, long userId) {
    Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    WorkEntity workEntity = EntityMapper.toEntity(work);
    workEntity.setModifiedDate(new Date());
    // The server sets a request draft's state
    workEntity.setIsDraft(true);
    if (work.getId() == 0) {
      workEntity.setId(null);
      workEntity.setCreatedDate(new Date());
      workEntity.setCreatorId(userId);
      workEntity = workDraftDAO.create(workEntity);
    } else {
      workEntity = workDraftDAO.update(workEntity);
    }
    return workEntity;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void copyProcessAttachmentsToDraft(Long draftId, WorkFlow workFlow, long userId) {
    processesAttachmentService.copyAttachmentsToEntity(userId,
                                                       workFlow.getId(),
                                                       WORKFLOW_ENTITY_TYPE,
                                                       draftId,
                                                       WORK_DRAFT_ENTITY_TYPE,
                                                       workFlow.getProjectId());
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
    // A project several processes feed is read once, and a status name is
    // listed once, at its lowest rank
    projectsIds.stream()
               .distinct()
               .forEach(projectId -> statuses.addAll(EntityMapper.toWorkStatuses(statusService.getStatuses(projectId))));
    statuses.sort(Comparator.comparing(WorkStatus::getRank));
    Set<String> names = new HashSet<>();
    return statuses.stream().filter(status -> names.add(status.getName())).collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkFlow> findWorkFlows(ProcessesFilter processesFilter, long userIdentityId, int offset, int limit) {
    Map.Entry<String, List<String>> userMemberships = getUserMemberships(userIdentityId);
    String userName = userMemberships.getKey();
    List<String> memberships = userMemberships.getValue();
    List<String> listedMemberships = listedMemberships(processesFilter, memberships);
    if (listsNothing(processesFilter, listedMemberships)) {
      return new ArrayList<>();
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
        Space space = getManagingSpace(workflow);
        workflow.setCanShowPending(space != null && (spaceService.isSuperManager(finalUserName) || spaceService.isMember(space, finalUserName)));
      }
    });
    return workFlows;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkFlow> findWorkFlowRights(ProcessesFilter processesFilter, long userIdentityId) {
    List<String> memberships = getUserMemberships(userIdentityId).getValue();
    List<String> listedMemberships = listedMemberships(processesFilter, memberships);
    if (listsNothing(processesFilter, listedMemberships)) {
      return new ArrayList<>();
    }
    return workFlowDAO.findWorkFlows(processesFilter, listedMemberships, 0, 0)
                      .stream()
                      .map(workflowEntity -> toWorkFlowRights(workflowEntity, memberships))
                      .toList();
  }

  /**
   * Maps a stored process to its id, its enabled flag and the rights the
   * memberships grant on it: the full mapping would also resolve its request
   * makers, a space or group read each.
   *
   * @param workflowEntity the stored process
   * @param memberships the user's memberships, null for a processes
   *          administrator
   * @return the process with those three fields only
   */
  private static WorkFlow toWorkFlowRights(WorkFlowEntity workflowEntity, List<String> memberships) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(workflowEntity.getId());
    workFlow.setEnabled(workflowEntity.isEnabled());
    workFlow.setAcl(EntityMapper.getACL(workflowEntity, memberships));
    return workFlow;
  }

  /**
   * @param processesFilter the listing's filter
   * @param memberships the user's memberships, null for a processes
   *          administrator
   * @return the memberships the listing matches the processes against: those
   *         given, or only the manager ones for the « I manage » filter,
   *         empty when that leaves none; null for a processes administrator
   */
  private static List<String> listedMemberships(ProcessesFilter processesFilter, List<String> memberships) {
    if (memberships != null && isManagerListing(processesFilter)) {
      // The processes a user manages are those whose managing space the user
      // administers, not every one whose space the user is a member of
      return memberships.stream()
                        .filter(membership -> membership.startsWith(ProcessesUtils.MANAGER_MEMBERSHIP_TYPE + ":"))
                        .toList();
    }
    return memberships;
  }

  /**
   * @param processesFilter the listing's filter
   * @param listedMemberships the memberships the listing matches against
   * @return whether the « I manage » listing has no manager membership to
   *         match, so lists nothing without a query
   */
  private static boolean listsNothing(ProcessesFilter processesFilter, List<String> listedMemberships) {
    return isManagerListing(processesFilter) && listedMemberships != null && listedMemberships.isEmpty();
  }

  /**
   * @param processesFilter the listing's filter
   * @return whether it is the « I manage » listing
   */
  private static boolean isManagerListing(ProcessesFilter processesFilter) {
    return processesFilter != null && Boolean.TRUE.equals(processesFilter.getManager());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorkFlows(ProcessesFilter processesFilter) {
    return workFlowDAO.countWorkFlows(processesFilter);
  }

  /**
   * Reads the managing space of a process: its stored space, else the space of
   * its task project, for a process stored before it had one.
   *
   * @param workFlow the process
   * @return the space, or null when none is found
   */
  private Space getManagingSpace(WorkFlow workFlow) {
    if (StringUtils.isNotBlank(workFlow.getSpaceId())) {
      Space storedSpace = spaceService.getSpaceById(workFlow.getSpaceId());
      if (storedSpace != null) {
        return storedSpace;
      }
    }
    return ProcessesUtils.getProjectParentSpace(workFlow.getProjectId());
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

  /**
   * Computes the request makers an update saves. The client sends only those
   * its editor shows, so a stored one it could not show (a user name, a
   * membership, a deleted group or space) is kept; a client that omits the
   * list (null) keeps the stored ones, and an empty list removes every shown
   * one. A stored group the group store fails to read fails the save, since
   * whether the user removed it can't be told.
   *
   * @param requestsCreators the request makers the client sent, or null
   * @param storedWorkFlowEntity the stored process, or null when not found
   * @return the request makers to save
   */
  private Set<String> getUpdatedManagers(List<CreatorIdentityEntity> requestsCreators, WorkFlowEntity storedWorkFlowEntity) {
    Set<String> storedManagers = storedWorkFlowEntity == null || storedWorkFlowEntity.getManager() == null ? Collections.emptySet()
                                                                                                         : storedWorkFlowEntity.getManager();
    if (requestsCreators == null) {
      return new HashSet<>(storedManagers);
    }
    Set<String> managers = getManagers(requestsCreators);
    storedManagers.stream().filter(manager -> !EntityMapper.isDisplayableCreator(manager)).forEach(managers::add);
    return managers;
  }

  /**
   * Converts the request makers a client sent to their stored entries: a
   * space's group id, else the identity's remote id.
   *
   * @param requestsCreators the request makers the client sent
   * @return the stored entries
   */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void requestProjectMove(long workFlowId, String requestedBy, long now) {
    workFlowDAO.requestMove(workFlowId, requestedBy, now);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ProjectMoveState claimProjectMove(long workFlowId, String token, long now, long until) {
    if (workFlowDAO.claimMove(workFlowId, token, now, until) == 0) {
      return null;
    }
    return readMoveState(workFlowId, token);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ProjectMoveState renewProjectMove(long workFlowId, String token, long now, long until) {
    if (workFlowDAO.renewMove(workFlowId, token, now, until) == 0) {
      return null;
    }
    return readMoveState(workFlowId, token);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void releaseProjectMove(long workFlowId, String token) {
    workFlowDAO.releaseMove(workFlowId, token);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean stopProjectMove(long workFlowId, long seq, String token) {
    return workFlowDAO.stopMove(workFlowId, seq, token) > 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean clearProjectMove(long workFlowId, long seq, String token, long now) {
    return workFlowDAO.clearMove(workFlowId, seq, token, now) > 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean rescheduleProjectMove(long workFlowId, long seq, String token, long now, long nextAttempt, boolean failed) {
    return workFlowDAO.rescheduleMove(workFlowId, seq, token, now, nextAttempt, failed) > 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getDueProjectMoves(long now) {
    return workFlowDAO.findDueMoves(now);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getStoredProjectId(long workFlowId) {
    return workFlowDAO.findProjectId(workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getRequestRecordTaskIds(long workFlowId, long afterTaskId, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("A page of request records needs a strictly positive limit");
    }
    return requestDAO.findTaskIdsAfter(workFlowId, afterTaskId, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteRequestRecord(long taskId) {
    requestDAO.deleteByTaskId(taskId);
  }

  /**
   * {@inheritDoc} One query: the request records restrict it with a correlated
   * EXISTS, so no list of ids is bound; a task with no status or no project is
   * outside the project.
   */
  @Override
  public long countRequestsOutsideProject(long workFlowId, long projectId) {
    TaskQuery taskQuery = requestsQuery(workFlowId);
    taskQuery.add(new PredicateCondition<Task>((cb, query, root) -> RequestDAO.outsideProject(cb, root, projectId)));
    try {
      return taskService.countTasks(taskQuery);
    } catch (Exception e) {
      throw new IllegalStateException("The requests of the process " + workFlowId + " outside the project " + projectId
          + " could not be counted", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TaskMoveResult moveTaskToProject(long taskId, long projectId) {
    TaskDto task;
    try {
      task = taskService.getTask(taskId);
    } catch (EntityNotFoundException e) {
      return TaskMoveResult.NOT_FOUND;
    }
    if (task == null) {
      return TaskMoveResult.NOT_FOUND;
    }
    StatusDto formerStatus = task.getStatus();
    ProjectDto formerProject = formerStatus == null ? null : formerStatus.getProject();
    if (formerProject != null && formerProject.getId() == projectId) {
      return TaskMoveResult.IN_PROJECT;
    }
    try {
      projectService.getProject(projectId);
    } catch (EntityNotFoundException e) {
      throw new IllegalStateException("The project " + projectId + " was not found", e);
    }
    if (formerProject != null) {
      removeProjectLabels(task, formerProject.getId());
    }
    StatusDto targetStatus = getMoveTargetStatus(formerStatus, projectId);
    // Task maps the status by its id
    StatusDto movedStatus = new StatusDto();
    movedStatus.setId(targetStatus.getId());
    movedStatus.setName(targetStatus.getName());
    task.setStatus(movedStatus);
    taskService.updateTask(task);
    return TaskMoveResult.MOVED;
  }

  /**
   * Reads the column a moved request lands in: the target project's column of
   * the former column's name, read as a label only (exactly, then case
   * aside), else the target's first column. It never creates a column.
   *
   * @param formerStatus the request's column before the move, or null
   * @param projectId the target project id
   * @return the target column
   * @throws IllegalArgumentException processes.project.noStatus when the
   *           target project has no column
   */
  private StatusDto getMoveTargetStatus(StatusDto formerStatus, long projectId) {
    String statusName = formerStatus == null ? null : formerStatus.getName();
    StatusDto targetStatus = statusName == null ? null : findStatusByName(projectId, statusName);
    if (targetStatus == null && statusName != null) {
      targetStatus = findStatusByNameIgnoreCase(projectId, statusName);
    }
    return targetStatus == null ? getRequestStatus(projectId) : targetStatus;
  }

  /**
   * Moves a request's task to its process's stored project when it is not
   * there yet. A process whose stored project no longer exists leaves the
   * task where it is.
   *
   * @param taskId the request's task id
   * @param currentProjectId the project the task is in, as the caller read it
   * @return the project the task is in after the call: the stored project, or
   *         the given one when the task has no request record or the stored
   *         project is not known
   */
  private long moveToStoredProject(long taskId, long currentProjectId) {
    Long workFlowId = getRequestWorkFlowId(taskId);
    Long storedProjectId = workFlowId == null ? null : workFlowDAO.findProjectId(workFlowId);
    if (storedProjectId == null || storedProjectId <= 0 || storedProjectId == currentProjectId) {
      return currentProjectId;
    }
    try {
      moveTaskToProject(taskId, storedProjectId);
      return storedProjectId;
    } catch (IllegalStateException e) {
      LOG.warn("The request {} stays in its project: its process's project {} was not found", taskId, storedProjectId);
      return currentProjectId;
    }
  }

  /**
   * Removes a task from its labels of a project, as the user of the current
   * conversation, else as the system.
   *
   * @param task the task
   * @param projectId the project whose labels are removed
   */
  private void removeProjectLabels(TaskDto task, long projectId) {
    ConversationState conversationState = ConversationState.getCurrent();
    org.exoplatform.services.security.Identity currentIdentity =
                                                               conversationState == null ? null : conversationState.getIdentity();
    if (currentIdentity == null) {
      currentIdentity = new org.exoplatform.services.security.Identity(IdentityConstants.SYSTEM);
    }
    List<LabelDto> labels = labelService.findLabelsByTask(task, projectId, currentIdentity, 0, 0);
    if (labels == null) {
      return;
    }
    for (LabelDto label : labels) {
      try {
        labelService.removeTaskFromLabel(task, label.getId());
      } catch (EntityNotFoundException e) {
        // Removed meanwhile: nothing left to remove
        LOG.debug("The label {} of the task {} was already removed", label.getId(), task.getId());
      }
    }
  }

  /**
   * Finds a status of a project whose stored name equals a name, both
   * trimmed, case aside: the one of lowest id when several do.
   *
   * @param projectId the project id
   * @param name the status name
   * @return the status, or null when none matches
   */
  private StatusDto findStatusByNameIgnoreCase(long projectId, String name) {
    List<StatusDto> statuses = statusService.getStatuses(projectId);
    if (statuses == null || name == null) {
      return null;
    }
    String trimmedName = name.trim();
    return statuses.stream()
                   .filter(statusDto -> statusDto.getName() != null && trimmedName.equalsIgnoreCase(statusDto.getName().trim()))
                   .min(Comparator.comparingLong(StatusDto::getId))
                   .orElse(null);
  }

  /**
   * Reads the move state of a process whose claim the pass holds.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @return the state, or null when the pass does not hold the claim
   */
  private ProjectMoveState readMoveState(long workFlowId, String token) {
    Object[] row = workFlowDAO.findMoveState(workFlowId, token);
    if (row == null) {
      return null;
    }
    return new ProjectMoveState(row[0] == null ? 0 : ((Number) row[0]).longValue(),
                                (String) row[1],
                                row[2] == null ? 0 : ((Number) row[2]).longValue(),
                                row[3] == null ? 0 : ((Number) row[3]).intValue());
  }
}
