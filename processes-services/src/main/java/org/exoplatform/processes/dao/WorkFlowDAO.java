package org.exoplatform.processes.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.services.security.MembershipEntry;

public class WorkFlowDAO extends GenericDAOJPAImpl<WorkFlowEntity, Long> {

  public List<WorkFlowEntity> findAllWorkFlowsByUser(long userId, int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findAllWorkFlowsByUser",
            WorkFlowEntity.class);
    query.setParameter("userId", userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }
  public List<WorkFlowEntity> findEnabledWorkFlowsByUser(long userId, int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findEnabledWorkFlowsByUser",
            WorkFlowEntity.class);
    query.setParameter("userId", userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<WorkFlowEntity> findAllWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findAllWorkFlows",
            WorkFlowEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  /**
   * Builds the listing query. Null memberships list every process: only a
   * processes administrator's listing passes them; any other user's is
   * restricted to the processes their memberships participate in. The filter's
   * ids, when set, restrict the listing further to those processes, none when
   * the list is empty.
   */
  private String buildWorkflowQuery(ProcessesFilter processesFilter, List<String> memberships) {

    String q = processesFilter.getQuery();
    Boolean enabled = processesFilter.getEnabled();
    Boolean manager = processesFilter.getManager();

    StringBuilder queryString =
      new StringBuilder("SELECT DISTINCT workFlow FROM WorkFlow workFlow");

    if (memberships != null) {
      if (Boolean.FALSE.equals(manager)) {
        queryString.append(" LEFT JOIN workFlow.manager manager");
      }
      queryString.append(" LEFT JOIN workFlow.participator participator");
    }
    List<String> predicates = new ArrayList<>();
    if (StringUtils.isNotEmpty(q)) {
      predicates.add("""
          (workFlow.title LIKE :query
          OR workFlow.description LIKE :query
          OR workFlow.summary LIKE :query)
          """);
    }
    if (enabled != null) {
      predicates.add("workFlow.enabled = :enabled");
    }
    if (processesFilter.getIds() != null) {
      // An empty id list restricts to no process
      predicates.add(processesFilter.getIds().isEmpty() ? "workFlow.id IS NULL" : "workFlow.id IN :ids");
    }
    if (memberships != null) {
      if (Boolean.FALSE.equals(manager)) {
        predicates.add("(manager IN :managers OR participator IN :memberships)");
      } else {
        predicates.add("participator IN :memberships");
      }
    }
    if (!predicates.isEmpty()) {
      queryString.append(" WHERE ")
        .append(StringUtils.join(predicates, " AND "));
    }

    return queryString.toString();
  }

  private Query createQuery(String queryString, ProcessesFilter processesFilter, List<String> memberships) {
    Query query = getEntityManager().createQuery(queryString);

    if (StringUtils.isNotEmpty(processesFilter.getQuery())) {
      query.setParameter("query", "%" + processesFilter.getQuery() + "%");
    }

    if (processesFilter.getEnabled() != null) {
      query.setParameter("enabled", processesFilter.getEnabled());
    }

    if (processesFilter.getIds() != null && !processesFilter.getIds().isEmpty()) {
      query.setParameter("ids", processesFilter.getIds());
    }

    if (memberships != null) {
      query.setParameter("memberships", memberships);
      if (Boolean.FALSE.equals(processesFilter.getManager())) {
        query.setParameter("managers", getMembershipGroups(memberships));
      }
    }
    return query;
  }

  /**
   * The entries the request creators of a process are matched against: the
   * group of every 'type:/group' membership, whatever its type, and a user name
   * as it is. A request creator is stored as a group id or a user name, so a
   * membership of any type in a creator group lists the process, as
   * {@link org.exoplatform.processes.Utils.EntityMapper#getACL} grants it.
   *
   * @param memberships the user name and the memberships of the user, as
   *          'type:/group'
   * @return the user name and the groups of the memberships, without duplicates
   */
  private List<String> getMembershipGroups(List<String> memberships) {
    return memberships.stream()
                      .map(WorkFlowDAO::toRequestCreatorEntry)
                      .distinct()
                      .collect(Collectors.toList());
  }

  /**
   * @param membership a 'type:/group' membership or a user name
   * @return the group of the membership, or the user name as it is
   */
  private static String toRequestCreatorEntry(String membership) {
    MembershipEntry membershipEntry = MembershipEntry.parse(membership);
    return membershipEntry == null ? membership : membershipEntry.getGroup();
  }

  private Query buildWorkflowQueryCriteria(ProcessesFilter processesFilter) {
    String q = processesFilter.getQuery();
    Boolean enabled = processesFilter.getEnabled();
    List<Predicate> predicates = new ArrayList<>();
    CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
    CriteriaQuery<WorkFlowEntity> criteriaQuery = criteriaBuilder.createQuery(WorkFlowEntity.class);
    Root<WorkFlowEntity> root = criteriaQuery.from(WorkFlowEntity.class);
    if (q != null) {
      List<Predicate> qPredicates = new ArrayList<>();
      qPredicates.add(criteriaBuilder.like(root.get("title"), "%" + q + "%"));
      qPredicates.add(criteriaBuilder.like(root.get("description"), "%" + q + "%"));
      qPredicates.add(criteriaBuilder.like(root.get("summary"), "%" + q + "%"));
      predicates.add(criteriaBuilder.or(qPredicates.toArray(new Predicate[0])));
    }
    if(enabled != null) {
      predicates.add(criteriaBuilder.and(criteriaBuilder.equal(root.get("enabled"), enabled)));
    }
    criteriaQuery.select(root).where(predicates.toArray(new Predicate[0]));
    criteriaQuery.orderBy(criteriaBuilder.desc(root.get("id")));
    Query query = getEntityManager().createQuery(criteriaQuery);
    return query;
  }

  public List<WorkFlowEntity> findWorkFlows(ProcessesFilter processesFilter, int offset, int limit) {
    Query query = buildWorkflowQueryCriteria(processesFilter);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public int countWorkFlows(ProcessesFilter processesFilter) {
    Query query = buildWorkflowQueryCriteria(processesFilter);
    return query.getMaxResults();
  }

  public List<WorkFlowEntity> findWorkFlows(ProcessesFilter processesFilter, List<String> memberships, int offset, int limit) {

    String queryString = buildWorkflowQuery(processesFilter, memberships);
    Query query = createQuery(queryString, processesFilter, memberships);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public int countWorkFlows(ProcessesFilter processesFilter, List<String> memberships) {
    String queryString = buildWorkflowQuery(processesFilter, memberships);
    Query query = createQuery(queryString, processesFilter, memberships);
    return query.getMaxResults();
  }


  public List<WorkFlowEntity> findEnabledWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findEnabledWorkFlows", WorkFlowEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<WorkFlowEntity> findDisabledWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findDisabledWorkFlows",
            WorkFlowEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  /**
   * Reads the processes of a space that feed some task projects, by title.
   *
   * @param projectIds the task project ids, at most a chunk the database
   *          accepts as parameters
   * @param spaceId the managing space id the processes must have
   * @return one row per process: its project id, its id and its title
   */
  public List<Object[]> findProcessesOfProjects(Collection<Long> projectIds, long spaceId) {
    if (projectIds == null || projectIds.isEmpty()) {
      return Collections.emptyList();
    }
    TypedQuery<Object[]> query = getEntityManager().createNamedQuery("WorkFlow.findProcessesOfProjects", Object[].class);
    query.setParameter("projectIds", projectIds);
    query.setParameter("spaceId", spaceId);
    return query.getResultList();
  }

  /**
   * Reads the first process feeding a task project.
   *
   * @param projectId the task project id
   * @return the process of lowest id feeding it, or null when none does
   * @deprecated a task project may now be fed by several processes: read the
   *             process of a request from its request record instead. Since
   *             7.3.0.50, for removal.
   */
  @Deprecated(since = "7.3.0.50", forRemoval = true)
  public WorkFlowEntity getWorkFlowByProjectId(long projectId) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.getWorkFlowByProjectId",
            WorkFlowEntity.class);
    query.setParameter("projectId", projectId);
    query.setMaxResults(1);
    List<WorkFlowEntity> workFlows = query.getResultList();
    return workFlows.isEmpty() ? null : workFlows.get(0);
  }

  /**
   * Reads the ids of every process feeding a task project.
   *
   * @param projectId the task project id
   * @return the process ids, ascending
   */
  public List<Long> findWorkFlowIdsByProjectId(long projectId) {
    return getEntityManager().createQuery("SELECT w.id FROM WorkFlow w WHERE w.projectId = :projectId ORDER BY w.id", Long.class)
                             .setParameter("projectId", projectId)
                             .getResultList();
  }

  /**
   * Flags a process whose project a save changed: its requests may be outside
   * its project until a pass moves them. The sequence is incremented, so a
   * pass started before the save writes none of its conditional updates, and
   * the next attempt is due at once. Runs in the caller's transaction.
   *
   * @param workFlowId the process id
   * @param requestedBy the user id of the editor who changed the project
   * @param now the current time, in epoch milliseconds
   * @return how many rows were updated, 1 when the process exists
   */
  public int requestMove(long workFlowId, String requestedBy, long now) {
    return getEntityManager().createNamedQuery("WorkFlow.requestMove")
                             .setParameter("id", workFlowId)
                             .setParameter("requestedBy", requestedBy)
                             .setParameter("now", now)
                             .executeUpdate();
  }

  /**
   * Claims the move of a process for one pass: it succeeds only when a move is
   * pending, its next attempt is due and no other pass holds an unexpired
   * claim. Runs in the caller's transaction.
   *
   * @param workFlowId the process id
   * @param token the pass's own random token
   * @param now the current time, in epoch milliseconds
   * @param until when the claim expires, in epoch milliseconds
   * @return 1 when the pass holds the claim, 0 otherwise
   */
  public int claimMove(long workFlowId, String token, long now, long until) {
    return getEntityManager().createNamedQuery("WorkFlow.claimMove")
                             .setParameter("id", workFlowId)
                             .setParameter("token", token)
                             .setParameter("now", now)
                             .setParameter("until", until)
                             .executeUpdate();
  }

  /**
   * Extends the claim a pass holds, while it has not expired.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @param now the current time, in epoch milliseconds
   * @param until the new expiry, in epoch milliseconds
   * @return 1 when the claim was extended, 0 when the pass no longer holds it
   */
  public int renewMove(long workFlowId, String token, long now, long until) {
    return getEntityManager().createNamedQuery("WorkFlow.renewMove")
                             .setParameter("id", workFlowId)
                             .setParameter("token", token)
                             .setParameter("now", now)
                             .setParameter("until", until)
                             .executeUpdate();
  }

  /**
   * Releases the claim a pass holds, if it still holds it.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @return 1 when the claim was released, 0 when the pass no longer held it
   */
  public int releaseMove(long workFlowId, String token) {
    return getEntityManager().createNamedQuery("WorkFlow.releaseMove")
                             .setParameter("id", workFlowId)
                             .setParameter("token", token)
                             .executeUpdate();
  }

  /**
   * Stops the move of a process whose project no longer exists: no pass runs
   * again until a save sets another project. Written only when no save changed
   * the project since the pass started, and the pass still holds the claim.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   * @return 1 when the move was stopped, 0 otherwise
   */
  public int stopMove(long workFlowId, long seq, String token) {
    return getEntityManager().createNamedQuery("WorkFlow.stopMove")
                             .setParameter("id", workFlowId)
                             .setParameter("seq", seq)
                             .setParameter("token", token)
                             .executeUpdate();
  }

  /**
   * Clears the move flag of a process, once a pass left no request outside
   * its project. Written only when no save changed the project since the pass
   * started, and the pass still holds an unexpired claim.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   * @param now the current time, in epoch milliseconds
   * @return 1 when the flag was cleared, 0 otherwise
   */
  public int clearMove(long workFlowId, long seq, String token, long now) {
    return getEntityManager().createNamedQuery("WorkFlow.clearMove")
                             .setParameter("id", workFlowId)
                             .setParameter("seq", seq)
                             .setParameter("token", token)
                             .setParameter("now", now)
                             .executeUpdate();
  }

  /**
   * Schedules the next pass of a process's move, conditioned as
   * {@link #clearMove(long, long, String, long)}.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   * @param now the current time, in epoch milliseconds
   * @param nextAttempt when the next pass is due, in epoch milliseconds
   * @param failed true to count a failed attempt, false for a verification
   *          pass after a pass that moved requests
   * @return 1 when the next pass was scheduled, 0 otherwise
   */
  public int rescheduleMove(long workFlowId, long seq, String token, long now, long nextAttempt, boolean failed) {
    return getEntityManager().createNamedQuery(failed ? "WorkFlow.retryMove" : "WorkFlow.verifyMove")
                             .setParameter("id", workFlowId)
                             .setParameter("seq", seq)
                             .setParameter("token", token)
                             .setParameter("now", now)
                             .setParameter("nextAttempt", nextAttempt)
                             .executeUpdate();
  }

  /**
   * Reads the move state of a process whose claim the pass holds.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @return the move sequence, the user id who requested the move, the
   *         process's project id and the failed attempts, or null when the
   *         pass does not hold the claim
   */
  public Object[] findMoveState(long workFlowId, String token) {
    List<Object[]> rows = getEntityManager().createNamedQuery("WorkFlow.findMoveState", Object[].class)
                                            .setParameter("id", workFlowId)
                                            .setParameter("token", token)
                                            .getResultList();
    return rows.isEmpty() ? null : rows.get(0);
  }

  /**
   * Reads the processes whose move a pass may claim now.
   *
   * @param now the current time, in epoch milliseconds
   * @return their ids, the most overdue first
   */
  public List<Long> findDueMoves(long now) {
    return getEntityManager().createNamedQuery("WorkFlow.findDueMoves", Long.class)
                             .setParameter("now", now)
                             .getResultList();
  }

  /**
   * Reads the stored project of a process, from the database.
   *
   * @param workFlowId the process id
   * @return its project id, or null when the process does not exist
   */
  public Long findProjectId(long workFlowId) {
    List<Long> rows = getEntityManager().createNamedQuery("WorkFlow.findProjectId", Long.class)
                                        .setParameter("id", workFlowId)
                                        .getResultList();
    return rows.isEmpty() ? null : rows.get(0);
  }

}
