export function initCometd() {
  const loc = window.location;
  const cometdContext = eXo.env.portal.cometdContext;
  const cometdToken = eXo.env.portal.cometdToken;
  cCometd.configure({
    url: `${loc.protocol}//${loc.hostname}${(loc.port && ':') || ''}${loc.port || ''}/${cometdContext}/cometd`,
    exoId: eXo.env.portal.userName,
    exoToken: cometdToken,
  });
}

export function getWorkFlows(itemsFilter, offset, limit, expand) {
  const formData = new FormData();
  if (itemsFilter) {
    Object.keys(itemsFilter).forEach(key => {
      const value = itemsFilter[key];
      if (value != null) {
        formData.append(key, value);
      }
    });
  }
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });

}
export function getWorks(itemsFilter, offset, limit, expand) {
  const formData = new FormData();
  if (itemsFilter) {
    Object.keys(itemsFilter).forEach(key => {
      const value = itemsFilter[key];
      if (value !== null) {
        formData.append(key, value);
      }
    });
  }
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

/**
 * Counts the current user's requests, as « My requests » lists them.
 *
 * @param {Object} itemsFilter the criteria: completed, status; null values are left out
 * @return {Promise} the count, as a number
 */
export function countWorks(itemsFilter) {
  const formData = new FormData();
  Object.keys(itemsFilter || {}).forEach(key => {
    const value = itemsFilter[key];
    if (value != null) {
      formData.append(key, value);
    }
  });
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works/count?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while counting the requests');
    } else {
      return resp.text().then(text => Number(text) || 0);
    }
  });
}

/**
 * Archives every request of the current user that is Validated, Refused or Canceled and not archived yet.
 *
 * @return {Promise} how many requests were archived, as a number
 */
export function archiveFinishedWorks() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works/archive`, {
    method: 'POST',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while archiving the requests');
    } else {
      return resp.text().then(text => Number(text) || 0);
    }
  });
}

/**
 * Creates a process.
 *
 * @param {Object} workflow the process, its null fields left out of the body
 * @return {Promise} the created process, or an error whose message is the
 *          code of a 400 refusal
 */
export function addNewWorkFlow(workflow) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workflow, (key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return readRefusal(resp, 'Error while saving a workflow');
    } else {
      return resp.json();
    }
  });
}

/**
 * Sends a request; a refusal rejects with the body of a 400 as its message.
 *
 * @param {Object} work the request
 * @return {Promise} the created request
 */
export function addWork(work) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(work, (key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return readRefusal(resp, 'Error while adding a new work');
    } else {
      return resp.json();
    }
  });
}

export function updateWork(work) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(work, (_key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while updating a work');
    } else {
      return resp.json();
    }
  });
}

export function isProcessesManager() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/permissions`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while checking user permissions');
    } else {
      return resp.text();
    }
  });
}

export function deleteWorkflowById(workflowId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflow/${workflowId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while deleting a workflow');
    } else {
      return resp.text();
    }
  });
}

/**
 * Updates a process.
 *
 * @param {Object} workflow the process, its null fields left out of the body
 * @return {Promise} the updated process as text, or an error whose message is
 *          the code of a 400 refusal
 */
export function updateWorkflow(workflow) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workflow, (key, value) => {
      if (value !== null) {
        return value;
      }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return readRefusal(resp, 'Error while saving a workflow');
    } else {
      return resp.text();
    }
  });
}

/**
 * Throws the error of a refused save, its message being the body the server
 * answers with a 400: a code, or a code and the refused field's key as
 * code:fieldKey. Any other refusal throws the generic message.
 *
 * @param {Response} resp the response of the save
 * @param {String} message the generic message
 * @return {Promise} a promise rejected with the error
 */
function readRefusal(resp, message) {
  if (!resp || resp.status !== 400) {
    return Promise.reject(new Error(message));
  }
  return resp.text().then(code => {
    throw new Error(code || message);
  });
}

/**
 * Lists the task projects a process of a managing space may feed, each with
 * the processes of that space that already feed it.
 *
 * @param {String} spaceId the managing space id
 * @param {Number} offset the offset
 * @param {Number} limit the page size
 * @return {Promise} the page, {projects: [{id, name, workflows: [{id, title}]}], size}
 */
export function getSpaceProjects(spaceId, offset, limit) {
  const params = new URLSearchParams({spaceId, offset: offset || 0, limit: limit || 0}).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/projects?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while listing the projects of a space');
    } else {
      return resp.json();
    }
  });
}

/**
 * Lists the labels of a task project a process of a managing space may feed.
 *
 * @param {Number} projectId the project id
 * @param {String} spaceId the managing space id
 * @return {Promise} the labels, [{id, name}] sorted by id
 */
export function getProjectLabels(projectId, spaceId) {
  const params = new URLSearchParams({spaceId}).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/projects/${projectId}/labels?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while listing the labels of a project');
    } else {
      return resp.json();
    }
  });
}

/**
 * Counts the requests of a process, open, completed or all, whatever task project they are in.
 *
 * @param {Number} workflowId the process id
 * @param {Boolean} [isCompleted] true to count the completed requests, false the open ones, left out to count them all
 * @return {Promise} the count as text
 */
export function countRequests(workflowId, isCompleted) {
  const query = typeof isCompleted === 'boolean' ? `?isCompleted=${isCompleted}` : '';
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows/${workflowId}/requests/count${query}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while counting the requests of a process');
    } else {
      return resp.text();
    }
  });
}

export function getWorkComments(workId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/tasks/comments/${workId}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error when getting task comments');
    }
  });
}

export function deleteWorkById(workId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/work/${workId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while deleting a work');
    } else {
      return resp.text();
    }
  });
}

export function updateWorkCompleted(workId, completed) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/work/${workId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      value: completed,
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while canceling a work');
    } else {
      return resp.json();
    }
  });
}

export function getWorkDrafts(itemsFilter, offset, limit, expand) {
  const formData = new FormData();
  if (itemsFilter) {
    Object.keys(itemsFilter).forEach(key => {
      const value = itemsFilter[key];
      if (value != null) {
        formData.append(key, value);
      }
    });
  }
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDrafts?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting work drafts');
    } else {
      return resp.json();
    }
  });
}

/**
 * Creates a request draft; a refusal rejects with the body of a 400 as its
 * message.
 *
 * @param {Object} workDraft the draft
 * @return {Promise} the created draft
 */
export function createWorkDraft(workDraft) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDraft`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workDraft, (key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return readRefusal(resp, 'Error while creating a new work draft');
    } else {
      return resp.json();
    }
  });
}

/**
 * Updates a request draft; a refusal rejects with the body of a 400 as its
 * message.
 *
 * @param {Object} workDraft the draft
 * @return {Promise} the updated draft, as text
 */
export function updateWorkDraft(workDraft) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDraft`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workDraft, (key, value) => {
      if (value !== null) {
        return value;
      }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      return readRefusal(resp, 'Error while updating a work draft');
    } else {
      return resp.text();
    }
  });
}

export function deleteWorkDraftById(draftId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDraft/${draftId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while deleting a work draft');
    } else {
      return resp.text();
    }
  });
}

export function getAvailableWorkStatuses() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works/statuses`, {
    credentials: 'include',
    method: 'GET',
  }).then((resp) => {
    if (resp || resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting available work statuses');
    }
  });
}

export function getWorkById(workId, expand) {
  const formData = new FormData();
  if (expand) {
    formData.append('expand', expand);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works/${workId}?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting work');
    } else {
      return resp.json();
    }
  });
}

export function getWorkflowById(workflowId, expand, translate) {
  const formData = new FormData();
  if (expand) {
    formData.append('expand', expand);
  }
  if (translate === false) {
    formData.append('translate', 'false');
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows/${workflowId}?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting workflow');
    } else {
      return resp.json();
    }
  });
}
